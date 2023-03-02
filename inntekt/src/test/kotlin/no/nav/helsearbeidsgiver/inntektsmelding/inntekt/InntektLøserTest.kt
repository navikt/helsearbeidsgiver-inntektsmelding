@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.HentTrengerImLøsning
import no.nav.helsearbeidsgiver.felles.InntektLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.test.date.februar
import no.nav.helsearbeidsgiver.felles.test.date.januar
import no.nav.helsearbeidsgiver.felles.test.mock.mockTrengerInntekt
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.test.resource.readResource
import no.nav.helsearbeidsgiver.felles.til
import no.nav.helsearbeidsgiver.inntekt.ArbeidsInntektInformasjon
import no.nav.helsearbeidsgiver.inntekt.ArbeidsinntektMaaned
import no.nav.helsearbeidsgiver.inntekt.Ident
import no.nav.helsearbeidsgiver.inntekt.Inntekt
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.inntekt.InntektskomponentResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

internal class InntektLøserTest {

    private val rapid = TestRapid()
    private var inntektLøser: InntektLøser
    private var inntektKlient: InntektKlient
    private val BEHOV_PDL = BehovType.FULLT_NAVN.toString()
    private val BEHOV_INNTEKT = BehovType.INNTEKT.toString()

    init {
        inntektKlient = mockk()
        inntektLøser = InntektLøser(rapid, inntektKlient)
    }

    @BeforeEach
    fun beforeEach() {
        rapid.reset()
    }

    @Test
    fun `skal håndtere feil mot inntektskomponenten`() {
        every {
            runBlocking {
                inntektKlient.hentInntektListe(any(), any(), any(), any(), any(), any(), any())
            }
        } throws RuntimeException()

        rapid.sendJson(
            Key.BEHOV to listOf(BEHOV_PDL, BEHOV_INNTEKT).toJson(String.serializer()),
            Key.ID to UUID.randomUUID().toJson(),
            Key.UUID to "uuid".toJson(),
            Key.IDENTITETSNUMMER to "abc".toJson(),
            Key.ORGNRUNDERENHET to "123456789".toJson(),
            Key.SESSION to sessionData()
                .toJson(
                    MapSerializer(
                        BehovType.serializer(),
                        HentTrengerImLøsning.serializer()
                    )
                )
        )

        val løsning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        val inntektLøsning = løsning.get(BEHOV_INNTEKT)?.toJsonElement()?.fromJson(InntektLøsning.serializer())
        assertNull(inntektLøsning?.value)
        assertNotNull(inntektLøsning?.error)
        assertEquals("Klarte ikke hente inntekt", inntektLøsning?.error?.melding)
    }

    @Test
    fun `skal publisere svar fra inntektskomponenten`() {
        val response = "response.json".readResource().fromJson(InntektskomponentResponse.serializer())
        every {
            runBlocking {
                inntektKlient.hentInntektListe(any(), any(), any(), any(), any(), any(), any())
            }
        } returns response

        rapid.sendJson(
            Key.BEHOV to listOf(BEHOV_PDL, BEHOV_INNTEKT).toJson(String.serializer()),
            Key.ID to UUID.randomUUID().toJson(),
            Key.UUID to "uuid".toJson(),
            Key.IDENTITETSNUMMER to "abc".toJson(),
            Key.ORGNRUNDERENHET to "123456789".toJson(),
            Key.SESSION to sessionData()
                .toJson(
                    MapSerializer(
                        BehovType.serializer(),
                        HentTrengerImLøsning.serializer()
                    )
                )
        )

        val løsning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        val inntektLøsning = løsning.get(BEHOV_INNTEKT)?.toJsonElement()?.fromJson(InntektLøsning.serializer())
        assertNull(inntektLøsning?.error)
        assertNotNull(inntektLøsning?.value)
    }

    @Test
    fun `skal finne riktig inntektsPeriode basert på sykmeldingsperioden - henter forrige måned`() {
        val inntektPeriode = finnInntektPeriode(
            listOf(
                Periode(
                    fom = LocalDate.of(2022, 4, 1),
                    tom = LocalDate.of(2022, 4, 20)
                )
            )
        )
        val expectedFom = LocalDate.of(2022, 1, 1)
        val expectedTom = LocalDate.of(2022, 3, 31)
        assertEquals(expectedFom, inntektPeriode.fom)
        assertEquals(expectedTom, inntektPeriode.tom)
    }

    @Test
    fun `skal sortere fra eldst til nyest ved flere sykmeldingsperioder`() {
        val inntektPeriode = finnInntektPeriode(
            listOf(
                (1.januar til 20.januar), // år 2018 er default
                (1.februar til 20.februar)
            )
        )
        // Skal velge februar, dermed blir tremånedersperioden november - januar
        val expectedFom = LocalDate.of(2017, 11, 1)
        val expectedTom = LocalDate.of(2018, 1, 31)
        assertEquals(expectedFom, inntektPeriode.fom)
        assertEquals(expectedTom, inntektPeriode.tom)
    }

    @Test
    fun `skal slå sammen perioder som er sammenhengende`() {
        val p1 = 1.februar(2023) til 6.februar(2023)
        val p2 = 7.februar(2023) til 19.februar(2023)
        val perioder = listOf(p2, p1)
        val bestemmende = finnBestemmendeFraværsdag(perioder)
        assertEquals(p1.fom, bestemmende)
    }

    @Test
    fun `skal finne riktig inntekt basert på sykmeldingsperioden som kommer i BEHOV`() {
        // TODO: Denne testen er litt grisete og tester mest egen mocke-logikk, men beholder foreløpig
        val svar = List(12) {
            lagInntektMaaned(YearMonth.of(2022, 12).minusMonths(it.toLong()), 1.0, "orgnr")
        }
        val til = slot<LocalDate>()
        val fra = slot<LocalDate>()
        every {
            runBlocking {
                inntektKlient.hentInntektListe(
                    ident = any(),
                    callId = any(),
                    navConsumerId = any(),
                    fraOgMed = capture(fra),
                    tilOgMed = capture(til),
                    filter = any(),
                    formaal = any()
                )
            }
        } answers {
            val t = YearMonth.from(til.captured)
            val f = YearMonth.from(fra.captured)
            val filtered = svar.filterNot { it.aarMaaned == null || it.aarMaaned!!.isBefore(f) || it.aarMaaned!!.isAfter(t) }
            InntektskomponentResponse(filtered)
        }

        rapid.sendJson(
            Key.BEHOV to listOf(BEHOV_PDL, BEHOV_INNTEKT).toJson(String.serializer()),
            Key.ID to UUID.randomUUID().toJson(),
            Key.UUID to "uuid".toJson(),
            Key.IDENTITETSNUMMER to "fnr".toJson(),
            Key.ORGNRUNDERENHET to "orgnr".toJson(),
            Key.SESSION to sessionData()
                .toJson(
                    MapSerializer(
                        BehovType.serializer(),
                        HentTrengerImLøsning.serializer()
                    )
                )
        )

        val løsning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        val inntektLøsning = løsning.get(BEHOV_INNTEKT)?.toJsonElement()?.fromJson(InntektLøsning.serializer())
        assertNull(inntektLøsning?.error)
        assertEquals(3, inntektLøsning?.value!!.historisk.size)
        assertEquals(YearMonth.of(2022, 4), inntektLøsning.value!!.historisk[0].maanedsnavn)
        assertEquals(YearMonth.of(2022, 3), inntektLøsning.value!!.historisk[1].maanedsnavn)
        assertEquals(YearMonth.of(2022, 2), inntektLøsning.value!!.historisk[2].maanedsnavn)
    }

    private fun sessionData() = mapOf(
        BehovType.HENT_TRENGER_IM to HentTrengerImLøsning(
            value = mockTrengerInntekt().copy(
                fnr = "fnr",
                orgnr = "orgnr",
                sykmeldingsperioder = sykmeldingsperioder(LocalDate.of(2022, 5, 1), LocalDate.of(2022, 5, 16)),
                forespurtData = emptyList()
            )
        )
    )

    private fun sykmeldingsperioder(fom: LocalDate, tom: LocalDate) = listOf(Periode(fom, tom))

    private fun lagInntektMaaned(mnd: YearMonth, beloep: Double, orgnr: String): ArbeidsinntektMaaned {
        return ArbeidsinntektMaaned(
            aarMaaned = mnd,
            arbeidsInntektInformasjon = ArbeidsInntektInformasjon(
                inntektListe = listOf(
                    Inntekt(
                        beloep = beloep,
                        virksomhet = Ident(orgnr)
                    )
                )
            )
        )
    }
}
