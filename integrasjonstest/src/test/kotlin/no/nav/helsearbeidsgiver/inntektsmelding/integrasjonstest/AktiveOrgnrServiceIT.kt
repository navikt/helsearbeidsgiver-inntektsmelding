package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.aareg.Ansettelsesperiode
import no.nav.helsearbeidsgiver.aareg.Arbeidsgiver
import no.nav.helsearbeidsgiver.aareg.Opplysningspliktig
import no.nav.helsearbeidsgiver.aareg.Periode
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.brreg.Virksomhet
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.AktiveArbeidsgivere
import no.nav.helsearbeidsgiver.felles.domene.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.tilArbeidsforhold
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.bjarneBetjent
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.maxMekker
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.kl
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import no.nav.helsearbeidsgiver.aareg.Arbeidsforhold as AAregArbeidsforhold

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AktiveOrgnrServiceIT : EndToEndTest() {
    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `Test hente aktive organisasjoner`() {
        val transaksjonId = UUID.randomUUID()

        coEvery { aaregClient.hentArbeidsforhold(any(), any()) } returns Mock.arbeidsforholdListe
        coEvery { altinnClient.hentRettighetOrganisasjoner(any()) } returns Mock.altinnOrganisasjonSet
        coEvery { brregClient.hentVirksomheter(any()) } returns listOf(Virksomhet(organisasjonsnummer = "810007842", navn = "ANSTENDIG PIGGSVIN BARNEHAGE"))
        coEvery { pdlKlient.personBolk(any()) } returns Mock.personer

        publish(
            Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FNR to Mock.fnr.toJson(),
                    Key.ARBEIDSGIVER_FNR to Mock.fnrAg.toJson(),
                ).toJson(),
        )

        redisConnection.get(RedisPrefix.AktiveOrgnr, transaksjonId) shouldBe Mock.GYLDIG_AKTIVE_ORGNR_RESPONSE

        val aktiveOrgnrMeldinger = messages.filter(EventName.AKTIVE_ORGNR_REQUESTED)

        aktiveOrgnrMeldinger
            .filter(BehovType.ARBEIDSGIVERE)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), data) shouldBe Mock.fnrAg
            }

        aktiveOrgnrMeldinger
            .filter(BehovType.HENT_ARBEIDSFORHOLD)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.FNR.les(Fnr.serializer(), data) shouldBe Mock.fnr
            }

        aktiveOrgnrMeldinger
            .filter(BehovType.HENT_PERSONER)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.FNR_LISTE.les(Fnr.serializer().set(), data) shouldBe setOf(Mock.fnr, Mock.fnrAg)
            }

        aktiveOrgnrMeldinger
            .filter(Key.ORG_RETTIGHETER)
            .firstAsMap()
            .also { melding ->
                val data = melding[Key.DATA].shouldNotBeNull().toMap()
                Key.ORG_RETTIGHETER.les(String.serializer().set(), data) shouldContainExactly Mock.altinnOrganisasjonSet.mapNotNull { it.orgnr }.toSet()
            }

        aktiveOrgnrMeldinger
            .filter(Key.ARBEIDSFORHOLD)
            .firstAsMap()
            .also { melding ->
                val data = melding[Key.DATA].shouldNotBeNull().toMap()
                Key.ARBEIDSFORHOLD.les(Arbeidsforhold.serializer().list(), data) shouldContainExactly Mock.arbeidsforholdListe.map { it.tilArbeidsforhold() }
            }

        aktiveOrgnrMeldinger
            .filter(BehovType.HENT_VIRKSOMHET_NAVN)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.ORGNR_UNDERENHETER.les(String.serializer().set(), data) shouldBe Mock.underenheter
            }

        aktiveOrgnrMeldinger
            .filter(Key.VIRKSOMHETER)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.VIRKSOMHETER.les(MapSerializer(String.serializer(), String.serializer()), data) shouldBe
                    mapOf("810007842" to "ANSTENDIG PIGGSVIN BARNEHAGE")
            }
    }

    @Test
    fun `ingen arbeidsforhold`() {
        val transaksjonId = UUID.randomUUID()

        coEvery { aaregClient.hentArbeidsforhold(any(), any()) } returns emptyList()
        coEvery { altinnClient.hentRettighetOrganisasjoner(any()) } returns Mock.altinnOrganisasjonSet
        coEvery { brregClient.hentVirksomheter(any()) } returns listOf(Virksomhet(organisasjonsnummer = "810007842", navn = "ANSTENDIG PIGGSVIN BARNEHAGE"))
        coEvery { pdlKlient.personBolk(any()) } returns Mock.personer

        publish(
            Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FNR to Mock.fnr.toJson(),
                    Key.ARBEIDSGIVER_FNR to Mock.fnrAg.toJson(),
                ).toJson(),
        )

        redisConnection.get(RedisPrefix.AktiveOrgnr, transaksjonId)?.parseJson() shouldBe Mock.resultatIngenArbeidsforholdJson

        val aktiveOrgnrMeldinger = messages.filter(EventName.AKTIVE_ORGNR_REQUESTED)

        aktiveOrgnrMeldinger
            .filter(BehovType.ARBEIDSGIVERE)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), data) shouldBe Mock.fnrAg
            }

        aktiveOrgnrMeldinger
            .filter(BehovType.HENT_ARBEIDSFORHOLD)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.FNR.les(Fnr.serializer(), data) shouldBe Mock.fnr
            }

        aktiveOrgnrMeldinger
            .filter(BehovType.HENT_PERSONER)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.FNR_LISTE.les(Fnr.serializer().set(), data) shouldBe setOf(Mock.fnr, Mock.fnrAg)
            }

        aktiveOrgnrMeldinger
            .filter(Key.ORG_RETTIGHETER)
            .firstAsMap()
            .also { melding ->
                val data = melding[Key.DATA].shouldNotBeNull().toMap()
                Key.ORG_RETTIGHETER.les(String.serializer().set(), data) shouldContainExactly Mock.altinnOrganisasjonSet.mapNotNull { it.orgnr }.toSet()
            }

        aktiveOrgnrMeldinger
            .filter(Key.ARBEIDSFORHOLD)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.ARBEIDSFORHOLD.les(Arbeidsforhold.serializer().list(), data) shouldBe emptyList()
            }
    }

    @Test
    fun `Ved feil under henting av personer så svarer service med feil`() {
        val transaksjonId = UUID.randomUUID()

        coEvery { aaregClient.hentArbeidsforhold(any(), any()) } returns Mock.arbeidsforholdListe
        coEvery { altinnClient.hentRettighetOrganisasjoner(any()) } returns Mock.altinnOrganisasjonSet
        coEvery { brregClient.hentVirksomheter(any()) } returns listOf(Virksomhet(organisasjonsnummer = "810007842", navn = "ANSTENDIG PIGGSVIN BARNEHAGE"))

        coEvery { pdlKlient.personBolk(any()) } throws IllegalArgumentException("Ingen folk å finne her!")

        val expectedFail =
            Fail(
                feilmelding = "Klarte ikke hente personer fra PDL.",
                event = EventName.AKTIVE_ORGNR_REQUESTED,
                transaksjonId = transaksjonId,
                forespoerselId = null,
                utloesendeMelding =
                    mapOf(
                        Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
                        Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
                        Key.UUID to transaksjonId.toJson(),
                        Key.DATA to
                            mapOf(
                                Key.FNR_LISTE to
                                    listOf(
                                        Mock.fnr,
                                        Mock.fnrAg,
                                    ).toJson(Fnr.serializer()),
                            ).toJson(),
                    ).toJson(),
            )

        publish(
            Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FNR to Mock.fnr.toJson(),
                    Key.ARBEIDSGIVER_FNR to Mock.fnrAg.toJson(),
                ).toJson(),
        )

        val response =
            redisConnection
                .get(RedisPrefix.AktiveOrgnr, transaksjonId)
                ?.fromJson(ResultJson.serializer())
                .shouldNotBeNull()

        response.success.shouldBeNull()
        response.failure.shouldNotBeNull().fromJson(String.serializer()) shouldBe expectedFail.feilmelding

        val actualFail =
            messages
                .filter(EventName.AKTIVE_ORGNR_REQUESTED)
                .filterFeil()
                .firstAsMap()
                .get(Key.FAIL)
                ?.fromJson(Fail.serializer())
                .shouldNotBeNull()

        actualFail.shouldBeEqualToIgnoringFields(expectedFail, Fail::utloesendeMelding)
        actualFail.utloesendeMelding.toMap() shouldContainExactly expectedFail.utloesendeMelding.toMap()
    }

    private object Mock {
        val GYLDIG_AKTIVE_ORGNR_RESPONSE =
            """
            {
                "success": {
                    "fulltNavn": "Bjarne Betjent",
                    "avsenderNavn": "Max Mekker",
                    "underenheter": [{"orgnrUnderenhet": "810007842", "virksomhetsnavn": "ANSTENDIG PIGGSVIN BARNEHAGE"}]
                }
            }
        """.removeJsonWhitespace()

        val resultatIngenArbeidsforholdJson =
            ResultJson(
                success =
                    AktiveArbeidsgivere(
                        fulltNavn = "Bjarne Betjent",
                        avsenderNavn = "Max Mekker",
                        underenheter = emptyList(),
                    ).toJson(AktiveArbeidsgivere.serializer()),
            ).toJson(ResultJson.serializer())

        val fnr = Fnr.genererGyldig()
        val fnrAg = Fnr.genererGyldig()

        val arbeidsforholdListe =
            listOf(
                AAregArbeidsforhold(
                    arbeidsgiver =
                        Arbeidsgiver(
                            type = "Underenhet",
                            organisasjonsnummer = "810007842",
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
                                fom = 1.januar,
                                tom = null,
                            ),
                        ),
                    registrert = 3.januar.kl(6, 30, 40, 50000),
                ),
            )

        val altinnOrganisasjonSet =
            setOf(
                AltinnOrganisasjon(
                    navn = "ANSTENDIG PIGGSVIN BYDEL",
                    type = "organisasjon",
                    orgnrHovedenhet = "810007702",
                ),
                AltinnOrganisasjon(
                    navn = "ANSTENDIG PIGGSVIN BARNEHAGE",
                    type = "organisasjon",
                    orgnr = "810007842",
                    orgnrHovedenhet = "810007702",
                ),
                AltinnOrganisasjon(
                    navn = "ANSTENDIG PIGGSVIN BRANNVESEN",
                    type = "organisasjon",
                    orgnr = "810008032",
                    orgnrHovedenhet = "810007702",
                ),
                AltinnOrganisasjon(
                    navn = "ANSTENDIG PIGGSVIN SYKEHJEM",
                    type = "organisasjon",
                    orgnr = "810007982",
                    orgnrHovedenhet = "810007702",
                ),
            )

        val underenheter = setOf("810007842")

        val personer =
            listOf(
                bjarneBetjent.copy(ident = fnr.verdi),
                maxMekker.copy(ident = fnrAg.verdi),
            )
    }
}
