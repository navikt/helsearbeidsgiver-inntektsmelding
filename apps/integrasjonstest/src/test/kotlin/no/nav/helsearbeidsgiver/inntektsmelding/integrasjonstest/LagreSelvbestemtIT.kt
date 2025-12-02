package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.PeriodeAapen
import no.nav.hag.simba.utils.felles.json.ansettelsesperioderSerializer
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.orgMapSerializer
import no.nav.hag.simba.utils.felles.json.personMapSerializer
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.test.mock.mockInntektsmeldingV1
import no.nav.hag.simba.utils.felles.test.mock.mockSkjemaInntektsmeldingSelvbestemt
import no.nav.hag.simba.utils.felles.test.mock.randomDigitString
import no.nav.helsearbeidsgiver.aareg.Periode
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Avsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykmeldt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.pdl.domene.FullPerson
import no.nav.helsearbeidsgiver.pdl.domene.PersonNavn
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.kl
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LagreSelvbestemtIT : EndToEndTest() {
    @BeforeEach
    fun setup() {
        truncateDatabase()
    }

    @Test
    fun `inntektsmelding lagres og prosesseres`() {
        val kontekstId: UUID = UUID.randomUUID()
        val nyInntektsmelding =
            Mock.inntektsmelding.copy(
                type =
                    Inntektsmelding.Type.Selvbestemt(
                        id = UUID.randomUUID(),
                    ),
                aarsakInnsending = AarsakInnsending.Ny,
            )

        coEvery { brregClient.hentOrganisasjonNavn(any()) } returns mapOf(Mock.organisasjon)
        coEvery { pdlKlient.personBolk(any()) } returns Mock.personer
        coEvery { aaregClient.hentAnsettelsesperioder(any(), any()) } returns Mock.ansettelsesperioder
        coEvery { agNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Mock.sakId
        coEvery { dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            OpprettOgFerdigstillResponse(
                journalpostId = Mock.journalpostId,
                journalpostFerdigstilt = true,
                melding = null,
                dokumenter = emptyList(),
            )

        publish(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ARBEIDSGIVER_FNR to Mock.avsenderFnr.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.copy(selvbestemtId = null).toJson(SkjemaInntektsmeldingSelvbestemt.serializer()),
                    Key.MOTTATT to Mock.mottatt.toJson(),
                ).toJson(),
        )

        val serviceMessages = messages.filter(EventName.SELVBESTEMT_IM_MOTTATT)

        // Data hentet
        serviceMessages
            .filter(Key.VIRKSOMHETER)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.VIRKSOMHETER.les(orgMapSerializer, data) shouldBe mapOf(Mock.organisasjon)
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
            .filter(Key.ANSETTELSESPERIODER)
            .firstAsMap()
            .also { melding ->
                val data = melding[Key.DATA].shouldNotBeNull().toMap()
                Key.ANSETTELSESPERIODER.les(ansettelsesperioderSerializer, data) shouldContainExactly
                    Mock.ansettelsesperioder.mapValues { (_, perioder) -> perioder.map { PeriodeAapen(it.fom, it.tom) }.toSet() }
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
            .firstAsMap()[Key.DATA]
            .shouldNotBeNull()
            .toMap()[Key.SELVBESTEMT_INNTEKTSMELDING]
            .shouldNotBeNull()
            .fromJson(Inntektsmelding.serializer())
            .shouldBeEqualToInntektsmelding(nyInntektsmelding)

        messages
            .filter(EventName.INNTEKTSMELDING_JOURNALFOERT)
            .firstAsMap()
            .shouldContainNokTilJournalfoeringOgDistribusjon(kontekstId, nyInntektsmelding, compareType = false)

        messages
            .filter(EventName.INNTEKTSMELDING_JOURNALPOST_ID_LAGRET)
            .firstAsMap()
            .shouldContainNokTilJournalfoeringOgDistribusjon(kontekstId, nyInntektsmelding, compareType = false)

        messages
            .filter(EventName.INNTEKTSMELDING_DISTRIBUERT)
            .firstAsMap()
            .shouldContainNokTilJournalfoeringOgDistribusjon(kontekstId, nyInntektsmelding, compareType = false)
    }

    @Test
    fun `endret inntektsmelding lagres og prosesseres, men uten opprettelse av sak`() {
        val kontekstId: UUID = UUID.randomUUID()

        coEvery { brregClient.hentOrganisasjonNavn(any()) } returns mapOf(Mock.organisasjon)
        coEvery { pdlKlient.personBolk(any()) } returns Mock.personer
        coEvery { aaregClient.hentAnsettelsesperioder(any(), any()) } returns Mock.ansettelsesperioder
        coEvery { dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            OpprettOgFerdigstillResponse(
                journalpostId = Mock.journalpostId,
                journalpostFerdigstilt = true,
                melding = null,
                dokumenter = emptyList(),
            )

        publish(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ARBEIDSGIVER_FNR to Mock.avsenderFnr.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.toJson(SkjemaInntektsmeldingSelvbestemt.serializer()),
                    Key.MOTTATT to Mock.mottatt.toJson(),
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
            .firstAsMap()[Key.DATA]
            .shouldNotBeNull()
            .toMap()[Key.SELVBESTEMT_INNTEKTSMELDING]
            .shouldNotBeNull()
            .fromJson(Inntektsmelding.serializer())
            .shouldBeEqualToInntektsmelding(Mock.inntektsmelding, compareType = true)

        messages
            .filter(EventName.INNTEKTSMELDING_JOURNALFOERT)
            .firstAsMap()
            .shouldContainNokTilJournalfoeringOgDistribusjon(kontekstId, Mock.inntektsmelding, compareType = true)

        messages
            .filter(EventName.INNTEKTSMELDING_JOURNALPOST_ID_LAGRET)
            .firstAsMap()
            .shouldContainNokTilJournalfoeringOgDistribusjon(kontekstId, Mock.inntektsmelding, compareType = true)

        messages
            .filter(EventName.INNTEKTSMELDING_DISTRIBUERT)
            .firstAsMap()
            .shouldContainNokTilJournalfoeringOgDistribusjon(kontekstId, Mock.inntektsmelding, compareType = true)
    }

    @Test
    fun `duplikat, endret inntektsmelding lagres, men prosesseres ikke`() {
        val kontekstId: UUID = UUID.randomUUID()

        selvbestemtImRepo.lagreIm(Mock.inntektsmelding)

        coEvery { brregClient.hentOrganisasjonNavn(any()) } returns mapOf(Mock.organisasjon)
        coEvery { pdlKlient.personBolk(any()) } returns Mock.personer
        coEvery { aaregClient.hentAnsettelsesperioder(any(), any()) } returns Mock.ansettelsesperioder

        publish(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ARBEIDSGIVER_FNR to Mock.avsenderFnr.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.toJson(SkjemaInntektsmeldingSelvbestemt.serializer()),
                    Key.MOTTATT to Mock.mottatt.toJson(),
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
            .filter(EventName.INNTEKTSMELDING_JOURNALFOERT)
            .all()
            .shouldBeEmpty()

        messages
            .filter(EventName.INNTEKTSMELDING_JOURNALPOST_ID_LAGRET)
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
        kontekstId: UUID,
        inntektsmelding: Inntektsmelding,
        compareType: Boolean,
    ) {
        this shouldContainAll
            mapOf(
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.JOURNALPOST_ID to Mock.journalpostId.toJson(),
            )

        Key.INNTEKTSMELDING.les(Inntektsmelding.serializer(), this).shouldBeEqualToInntektsmelding(inntektsmelding, compareType)
    }

    private object Mock {
        private val orgnr = Orgnr.genererGyldig()

        val avsenderFnr = Fnr.genererGyldig()
        val sakId = UUID.randomUUID().toString()
        val journalpostId = randomDigitString(18)
        val mottatt = 17.desember.kl(3, 4, 0, 0)
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

        val organisasjon = orgnr to "Innadvente Eiendomsmeglere"

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

        val ansettelsesperioder =
            mapOf(
                orgnr to
                    setOf(
                        Periode(
                            fom = 8.oktober,
                            tom = null,
                        ),
                    ),
            )

        val inntektsmelding =
            mockInntektsmeldingV1().copy(
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
                    Avsender(
                        orgnr = orgnr,
                        orgNavn = organisasjon.second,
                        navn = "Jan Eggum",
                        tlf = skjema.avsender.tlf,
                    ),
                aarsakInnsending = AarsakInnsending.Endring,
                vedtaksperiodeId = skjema.vedtaksperiodeId.shouldNotBeNull(),
            )
    }
}
