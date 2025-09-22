package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import kotlinx.serialization.builtins.serializer
import no.nav.hag.simba.kontrakt.domene.arbeidsgiver.AktiveArbeidsgivere
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.domene.PeriodeAapen
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.ansettelsesperioderSerializer
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.orgMapSerializer
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.helsearbeidsgiver.aareg.Periode
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.bjarneBetjent
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.maxMekker
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AktiveOrgnrServiceIT : EndToEndTest() {
    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `Henter aktive organisasjoner`() {
        val kontekstId = UUID.randomUUID()

        coEvery { aaregClient.hentAnsettelsesperioder(any(), any()) } returns Mock.ansettelsesperioder
        coEvery { altinnClient.hentTilganger(any()) } returns Mock.altinnOrganisasjonSet
        coEvery { brregClient.hentOrganisasjonNavn(any()) } returns mapOf(Mock.orgnr to Mock.orgNavn)
        coEvery { pdlKlient.personBolk(any()) } returns Mock.personer

        publish(
            Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FNR to Mock.fnr.toJson(),
                    Key.ARBEIDSGIVER_FNR to Mock.fnrAg.toJson(),
                ).toJson(),
        )

        redisConnection.get(RedisPrefix.AktiveOrgnr, kontekstId) shouldBe Mock.GYLDIG_AKTIVE_ORGNR_RESPONSE

        val aktiveOrgnrMeldinger = messages.filter(EventName.AKTIVE_ORGNR_REQUESTED)

        aktiveOrgnrMeldinger
            .filter(BehovType.ARBEIDSGIVERE)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), data) shouldBe Mock.fnrAg
            }

        aktiveOrgnrMeldinger
            .filter(BehovType.HENT_ANSETTELSESPERIODER)
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
                Key.ORG_RETTIGHETER.les(String.serializer().set(), data) shouldContainExactly Mock.altinnOrganisasjonSet
            }

        aktiveOrgnrMeldinger
            .filter(Key.ANSETTELSESPERIODER)
            .firstAsMap()
            .also { melding ->
                val data = melding[Key.DATA].shouldNotBeNull().toMap()
                Key.ANSETTELSESPERIODER.les(ansettelsesperioderSerializer, data) shouldContainExactly
                    Mock.ansettelsesperioder.mapValues { (_, perioder) -> perioder.map { PeriodeAapen(it.fom, it.tom) }.toSet() }
            }

        aktiveOrgnrMeldinger
            .filter(BehovType.HENT_VIRKSOMHET_NAVN)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.ORGNR_UNDERENHETER.les(Orgnr.serializer().set(), data) shouldBe setOf(Mock.orgnr)
            }

        aktiveOrgnrMeldinger
            .filter(Key.VIRKSOMHETER)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.VIRKSOMHETER.les(orgMapSerializer, data) shouldBe mapOf(Mock.orgnr to Mock.orgNavn)
            }
    }

    @Test
    fun `ingen arbeidsforhold`() {
        val kontekstId = UUID.randomUUID()

        coEvery { aaregClient.hentAnsettelsesperioder(any(), any()) } returns emptyMap()
        coEvery { altinnClient.hentTilganger(any()) } returns Mock.altinnOrganisasjonSet
        coEvery { brregClient.hentOrganisasjonNavn(any()) } returns mapOf(Mock.orgnr to Mock.orgNavn)
        coEvery { pdlKlient.personBolk(any()) } returns Mock.personer

        publish(
            Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FNR to Mock.fnr.toJson(),
                    Key.ARBEIDSGIVER_FNR to Mock.fnrAg.toJson(),
                ).toJson(),
        )

        redisConnection.get(RedisPrefix.AktiveOrgnr, kontekstId)?.parseJson() shouldBe Mock.resultatIngenArbeidsforholdJson

        val aktiveOrgnrMeldinger = messages.filter(EventName.AKTIVE_ORGNR_REQUESTED)

        aktiveOrgnrMeldinger
            .filter(BehovType.ARBEIDSGIVERE)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), data) shouldBe Mock.fnrAg
            }

        aktiveOrgnrMeldinger
            .filter(BehovType.HENT_ANSETTELSESPERIODER)
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
                Key.ORG_RETTIGHETER.les(String.serializer().set(), data) shouldContainExactly Mock.altinnOrganisasjonSet
            }

        aktiveOrgnrMeldinger
            .filter(Key.ANSETTELSESPERIODER)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.ANSETTELSESPERIODER.les(ansettelsesperioderSerializer, data) shouldBe emptyMap()
            }
    }

    @Test
    fun `Ved feil under henting av personer så svarer service med feil`() {
        val kontekstId = UUID.randomUUID()

        coEvery { aaregClient.hentAnsettelsesperioder(any(), any()) } returns Mock.ansettelsesperioder
        coEvery { altinnClient.hentTilganger(any()) } returns Mock.altinnOrganisasjonSet
        coEvery { brregClient.hentOrganisasjonNavn(any()) } returns mapOf(Mock.orgnr to Mock.orgNavn)

        coEvery { pdlKlient.personBolk(any()) } throws IllegalArgumentException("Ingen folk å finne her!")

        val expectedFail =
            Fail(
                feilmelding = "Klarte ikke hente personer fra PDL.",
                kontekstId = kontekstId,
                utloesendeMelding =
                    mapOf(
                        Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
                        Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
                        Key.KONTEKST_ID to kontekstId.toJson(),
                        Key.DATA to
                            mapOf(
                                Key.SVAR_KAFKA_KEY to KafkaKey(Mock.fnr).toJson(),
                                Key.FNR_LISTE to
                                    listOf(
                                        Mock.fnr,
                                        Mock.fnrAg,
                                    ).toJson(Fnr.serializer()),
                            ).toJson(),
                    ),
            )

        publish(
            Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FNR to Mock.fnr.toJson(),
                    Key.ARBEIDSGIVER_FNR to Mock.fnrAg.toJson(),
                ).toJson(),
        )

        val response =
            redisConnection
                .get(RedisPrefix.AktiveOrgnr, kontekstId)
                ?.fromJson(ResultJson.serializer())
                .shouldNotBeNull()

        response.success.shouldBeNull()
        response.failure.shouldNotBeNull().fromJson(String.serializer()) shouldBe expectedFail.feilmelding

        val actualFail =
            messages
                .filterFeil()
                .firstAsMap()
                .get(Key.FAIL)
                ?.fromJson(Fail.serializer())
                .shouldNotBeNull()

        actualFail.shouldBeEqualToIgnoringFields(expectedFail, Fail::utloesendeMelding)
        actualFail.utloesendeMelding shouldContainExactly expectedFail.utloesendeMelding
    }

    private object Mock {
        val orgnr = Orgnr("810007842")
        val orgNavn = "ANSTENDIG PIGGSVIN BARNEHAGE"

        val GYLDIG_AKTIVE_ORGNR_RESPONSE =
            """
            {
                "success": {
                    "sykmeldtNavn": "Bjarne Betjent",
                    "avsenderNavn": "Max Mekker",
                    "arbeidsgivere": [{"orgnr": "$orgnr", "orgNavn": "$orgNavn"}]
                }
            }
        """.removeJsonWhitespace()

        val resultatIngenArbeidsforholdJson =
            ResultJson(
                success =
                    AktiveArbeidsgivere(
                        sykmeldtNavn = "Bjarne Betjent",
                        avsenderNavn = "Max Mekker",
                        arbeidsgivere = emptyList(),
                    ).toJson(AktiveArbeidsgivere.serializer()),
            ).toJson()

        val fnr = Fnr.genererGyldig()
        val fnrAg = Fnr.genererGyldig()

        val ansettelsesperioder =
            mapOf(
                orgnr to
                    setOf(
                        Periode(
                            fom = 1.januar,
                            tom = null,
                        ),
                    ),
            )

        val altinnOrganisasjonSet = setOf(orgnr.verdi, "810008032", "810007982")

        val personer =
            listOf(
                bjarneBetjent.copy(ident = fnr.verdi),
                maxMekker.copy(ident = fnrAg.verdi),
            )
    }
}
