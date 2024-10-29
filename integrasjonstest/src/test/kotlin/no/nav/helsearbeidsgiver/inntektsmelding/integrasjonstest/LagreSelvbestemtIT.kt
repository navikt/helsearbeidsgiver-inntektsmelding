package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.aareg.Ansettelsesperiode
import no.nav.helsearbeidsgiver.aareg.Arbeidsgiver
import no.nav.helsearbeidsgiver.aareg.Opplysningspliktig
import no.nav.helsearbeidsgiver.aareg.Periode
import no.nav.helsearbeidsgiver.brreg.Virksomhet
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykmeldt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.felles.test.mock.randomDigitString
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.tilArbeidsforhold
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.pdl.domene.FullPerson
import no.nav.helsearbeidsgiver.pdl.domene.PersonNavn
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.kl
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import no.nav.helsearbeidsgiver.aareg.Arbeidsforhold as KlientArbeidsforhold

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LagreSelvbestemtIT : EndToEndTest() {
    @BeforeEach
    fun setup() {
        truncateDatabase()
    }

    @Test
    fun `inntektsmelding lagres og prosesseres`() {
        val transaksjonId: UUID = UUID.randomUUID()
        val nyInntektsmelding =
            Mock.inntektsmelding.copy(
                type =
                    Inntektsmelding.Type.Selvbestemt(
                        id = UUID.randomUUID(),
                    ),
                aarsakInnsending = AarsakInnsending.Ny,
            )

        coEvery { brregClient.hentVirksomheter(any()) } returns listOf(Mock.virksomhet)
        coEvery { pdlKlient.personBolk(any()) } returns Mock.personer
        coEvery { aaregClient.hentArbeidsforhold(any(), any()) } returns Mock.arbeidsforhold
        coEvery { arbeidsgiverNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any()) } returns Mock.sakId
        coEvery { dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any()) } returns
            OpprettOgFerdigstillResponse(
                journalpostId = Mock.journalpostId,
                journalpostFerdigstilt = true,
                melding = null,
                dokumenter = emptyList(),
            )

        publish(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.copy(selvbestemtId = null).toJson(SkjemaInntektsmeldingSelvbestemt.serializer()),
                    Key.ARBEIDSGIVER_FNR to Mock.avsenderFnr.toJson(),
                ).toJson(),
        )

        val serviceMessages = messages.filter(EventName.SELVBESTEMT_IM_MOTTATT)

        // Data hentet
        serviceMessages
            .filter(Key.VIRKSOMHETER)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.VIRKSOMHETER.les(MapSerializer(String.serializer(), String.serializer()), data) shouldBe
                    mapOf(Mock.virksomhet.organisasjonsnummer to Mock.virksomhet.navn)
            }

        // Data hentet
        serviceMessages
            .filter(Key.PERSONER)
            .firstAsMap()
            .also { melding ->
                val data = melding[Key.DATA].shouldNotBeNull().toMap()
                val personer = Key.PERSONER.les(personMapSerializer, data).map { it.key.verdi to it.value.navn }

                personer shouldBe Mock.personer.map { it.ident to it.navn.fulltNavn() }
            }

        // Data hentet
        serviceMessages
            .filter(Key.ARBEIDSFORHOLD)
            .firstAsMap()
            .also { melding ->
                val data = melding[Key.DATA].shouldNotBeNull().toMap()
                Key.ARBEIDSFORHOLD.les(Arbeidsforhold.serializer().list(), data) shouldContainExactly Mock.arbeidsforhold.map { it.tilArbeidsforhold() }
            }

        // Lagring forespurt
        serviceMessages
            .filter(BehovType.LAGRE_SELVBESTEMT_IM)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.SELVBESTEMT_INNTEKTSMELDING.les(Inntektsmelding.serializer(), data).shouldBeEqualToInntektsmelding(nyInntektsmelding)
            }

        // Lagring utført, uten duplikat
        serviceMessages
            .filter(Key.ER_DUPLIKAT_IM)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), data).shouldBeFalse()
            }

        // Opprettelse av sak forespurt
        serviceMessages
            .filter(BehovType.OPPRETT_SELVBESTEMT_SAK)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.SELVBESTEMT_INNTEKTSMELDING.les(Inntektsmelding.serializer(), data).shouldBeEqualToInntektsmelding(nyInntektsmelding)
            }

        // Opprettelse av sak utført
        serviceMessages
            .filter(Key.SAK_ID)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.SAK_ID.les(String.serializer(), data) shouldBe Mock.sakId
            }

        // Service ferdig
        messages
            .filter(EventName.SELVBESTEMT_IM_LAGRET)
            .firstAsMap()[Key.SELVBESTEMT_INNTEKTSMELDING]
            .shouldNotBeNull()
            .fromJson(Inntektsmelding.serializer())
            .shouldBeEqualToInntektsmelding(nyInntektsmelding)

        messages
            .filter(EventName.SELVBESTEMT_IM_LAGRET)
            .filter(BehovType.LAGRE_JOURNALPOST_ID)
            .firstAsMap()
            .shouldContainNokTilJournalfoeringOgDistribusjon(transaksjonId, nyInntektsmelding, compareType = false)

        messages
            .filter(EventName.INNTEKTSMELDING_JOURNALFOERT)
            .firstAsMap()
            .shouldContainNokTilJournalfoeringOgDistribusjon(transaksjonId, nyInntektsmelding, compareType = false)

        messages
            .filter(EventName.INNTEKTSMELDING_DISTRIBUERT)
            .firstAsMap()
            .shouldContainNokTilJournalfoeringOgDistribusjon(transaksjonId, nyInntektsmelding, compareType = false)
    }

    @Test
    fun `endret inntektsmelding lagres og prosesseres, men uten opprettelse av sak`() {
        val transaksjonId: UUID = UUID.randomUUID()

        coEvery { brregClient.hentVirksomheter(any()) } returns listOf(Mock.virksomhet)
        coEvery { pdlKlient.personBolk(any()) } returns Mock.personer
        coEvery { aaregClient.hentArbeidsforhold(any(), any()) } returns Mock.arbeidsforhold
        coEvery { dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any()) } returns
            OpprettOgFerdigstillResponse(
                journalpostId = Mock.journalpostId,
                journalpostFerdigstilt = true,
                melding = null,
                dokumenter = emptyList(),
            )

        publish(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.toJson(SkjemaInntektsmeldingSelvbestemt.serializer()),
                    Key.ARBEIDSGIVER_FNR to Mock.avsenderFnr.toJson(),
                ).toJson(),
        )

        val serviceMessages = messages.filter(EventName.SELVBESTEMT_IM_MOTTATT)

        // Lagring utført, uten duplikat
        serviceMessages
            .filter(Key.ER_DUPLIKAT_IM)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), data).shouldBeFalse()
            }

        // Opprettelse av sak _ikke_ forespurt
        serviceMessages
            .filter(BehovType.OPPRETT_SELVBESTEMT_SAK)
            .all()
            .shouldBeEmpty()

        // Opprettelse av sak _ikke_ utført
        serviceMessages
            .filter(Key.SAK_ID)
            .all()
            .shouldBeEmpty()

        // Service ferdig
        messages
            .filter(EventName.SELVBESTEMT_IM_LAGRET)
            .firstAsMap()[Key.SELVBESTEMT_INNTEKTSMELDING]
            .shouldNotBeNull()
            .fromJson(Inntektsmelding.serializer())
            .shouldBeEqualToInntektsmelding(Mock.inntektsmelding, compareType = true)

        messages
            .filter(EventName.SELVBESTEMT_IM_LAGRET)
            .filter(BehovType.LAGRE_JOURNALPOST_ID)
            .firstAsMap()
            .shouldContainNokTilJournalfoeringOgDistribusjon(transaksjonId, Mock.inntektsmelding, compareType = true)

        messages
            .filter(EventName.INNTEKTSMELDING_JOURNALFOERT)
            .firstAsMap()
            .shouldContainNokTilJournalfoeringOgDistribusjon(transaksjonId, Mock.inntektsmelding, compareType = true)

        messages
            .filter(EventName.INNTEKTSMELDING_DISTRIBUERT)
            .firstAsMap()
            .shouldContainNokTilJournalfoeringOgDistribusjon(transaksjonId, Mock.inntektsmelding, compareType = true)
    }

    @Test
    fun `duplikat, endret inntektsmelding lagres, men prosesseres ikke`() {
        val transaksjonId: UUID = UUID.randomUUID()

        selvbestemtImRepo.lagreIm(Mock.inntektsmelding)

        coEvery { brregClient.hentVirksomheter(any()) } returns listOf(Mock.virksomhet)
        coEvery { pdlKlient.personBolk(any()) } returns Mock.personer
        coEvery { aaregClient.hentArbeidsforhold(any(), any()) } returns Mock.arbeidsforhold

        publish(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.toJson(SkjemaInntektsmeldingSelvbestemt.serializer()),
                    Key.ARBEIDSGIVER_FNR to Mock.avsenderFnr.toJson(),
                ).toJson(),
        )

        val serviceMessages = messages.filter(EventName.SELVBESTEMT_IM_MOTTATT)

        // Lagring utført, med duplikat
        serviceMessages
            .filter(Key.ER_DUPLIKAT_IM)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.SELVBESTEMT_INNTEKTSMELDING.les(Inntektsmelding.serializer(), data).shouldBeEqualToInntektsmelding(Mock.inntektsmelding, compareType = true)
            }

        serviceMessages
            .filter(Key.ER_DUPLIKAT_IM)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), data).shouldBeTrue()
            }

        // Opprettelse av sak _ikke_ forespurt
        serviceMessages
            .filter(BehovType.OPPRETT_SELVBESTEMT_SAK)
            .all()
            .shouldBeEmpty()

        // Opprettelse av sak _ikke_ utført
        serviceMessages
            .filter(Key.SAK_ID)
            .all()
            .shouldBeEmpty()

        // Service ferdig
        messages
            .filter(EventName.SELVBESTEMT_IM_LAGRET)
            .all()
            .shouldBeEmpty()

        messages
            .filter(EventName.SELVBESTEMT_IM_LAGRET)
            .filter(BehovType.LAGRE_JOURNALPOST_ID)
            .all()
            .shouldBeEmpty()

        messages
            .filter(EventName.INNTEKTSMELDING_JOURNALFOERT)
            .all()
            .shouldBeEmpty()

        messages
            .filter(EventName.INNTEKTSMELDING_DISTRIBUERT)
            .all()
            .shouldBeEmpty()
    }

    private fun Inntektsmelding.shouldBeEqualToInntektsmelding(
        other: Inntektsmelding,
        compareType: Boolean = false,
    ) {
        shouldBeEqualToIgnoringFields(other, Inntektsmelding::id, Inntektsmelding::type, Inntektsmelding::mottatt)
        if (compareType) {
            type shouldBe other.type
        }
    }

    private fun Map<Key, JsonElement>.shouldContainNokTilJournalfoeringOgDistribusjon(
        transaksjonId: UUID,
        inntektsmelding: Inntektsmelding,
        compareType: Boolean,
    ) {
        this shouldContainAll
            mapOf(
                Key.UUID to transaksjonId.toJson(),
                Key.JOURNALPOST_ID to Mock.journalpostId.toJson(),
            )

        Key.INNTEKTSMELDING.les(Inntektsmelding.serializer(), this).shouldBeEqualToInntektsmelding(inntektsmelding, compareType)
    }

    private object Mock {
        private val orgnr = Orgnr.genererGyldig()

        val avsenderFnr = Fnr.genererGyldig()
        val sakId = UUID.randomUUID().toString()
        val journalpostId = randomDigitString(18)
        val skjema =
            mockSkjemaInntektsmeldingSelvbestemt().let {
                it.copy(
                    selvbestemtId = UUID.randomUUID(),
                    avsender =
                        it.avsender.copy(
                            orgnr = orgnr,
                        ),
                )
            }

        val virksomhet =
            Virksomhet(
                navn = "Innadvente Eiendomsmeglere",
                organisasjonsnummer = orgnr.verdi,
            )

        val personer =
            listOf(
                FullPerson(
                    ident = skjema.sykmeldtFnr.verdi,
                    navn =
                        PersonNavn(
                            fornavn = "Åge",
                            mellomnavn = null,
                            etternavn = "Aleksandersen",
                        ),
                    foedselsdato = 22.april,
                ),
                FullPerson(
                    ident = avsenderFnr.verdi,
                    navn =
                        PersonNavn(
                            fornavn = "Jan",
                            mellomnavn = null,
                            etternavn = "Eggum",
                        ),
                    foedselsdato = 30.august,
                ),
            )

        val arbeidsforhold =
            listOf(
                KlientArbeidsforhold(
                    arbeidsgiver =
                        Arbeidsgiver(
                            type = "Underenhet",
                            organisasjonsnummer = orgnr.verdi,
                        ),
                    opplysningspliktig =
                        Opplysningspliktig(
                            type = "ikke brukt",
                            organisasjonsnummer = "ikke brukt heller",
                        ),
                    arbeidsavtaler = emptyList(),
                    ansettelsesperiode =
                        Ansettelsesperiode(
                            Periode(
                                fom = 8.oktober,
                                tom = null,
                            ),
                        ),
                    registrert = 7.oktober.kl(20, 0, 0, 0),
                ),
            )

        val inntektsmelding =
            mockInntektsmeldingV1().let {
                it.copy(
                    type =
                        Inntektsmelding.Type.Selvbestemt(
                            id = skjema.selvbestemtId.shouldNotBeNull(),
                        ),
                    sykmeldt =
                        Sykmeldt(
                            fnr = skjema.sykmeldtFnr,
                            navn = "Åge Aleksandersen",
                        ),
                    avsender =
                        it.avsender.copy(
                            orgnr = orgnr,
                            orgNavn = virksomhet.navn,
                            navn = "Jan Eggum",
                        ),
                    aarsakInnsending = AarsakInnsending.Endring,
                    vedtaksperiodeId = skjema.vedtaksperiodeId.shouldNotBeNull(),
                )
            }
    }
}
