@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.HentTrengerImLøsning
import no.nav.helsearbeidsgiver.felles.InntektLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.felles.test.date.februar
import no.nav.helsearbeidsgiver.felles.test.mock.mockTrengerInntekt
import no.nav.helsearbeidsgiver.inntekt.ArbeidsInntektInformasjon
import no.nav.helsearbeidsgiver.inntekt.ArbeidsinntektMaaned
import no.nav.helsearbeidsgiver.inntekt.Ident
import no.nav.helsearbeidsgiver.inntekt.Inntekt
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.inntekt.InntektskomponentResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.IllegalArgumentException
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

fun String.loadFromResources(): String {
    return ClassLoader.getSystemResource(this).readText()
}

internal class InntektLøserTest {

    private val rapid = TestRapid()
    private var inntektLøser: InntektLøser
    private var inntektKlient: InntektKlient
    private val BEHOV_PDL = BehovType.FULLT_NAVN.toString()
    private val BEHOV_INNTEKT = BehovType.INNTEKT.toString()
    internal val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())

    init {
        inntektKlient = mockk<InntektKlient>()
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
        val melding = mapOf(
            "@behov" to listOf(BEHOV_PDL, BEHOV_INNTEKT),
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "identitetsnummer" to "abc",
            Key.ORGNRUNDERENHET.str to "123456789",
            Key.SESSION.str to mapOf(
                BehovType.HENT_TRENGER_IM to HentTrengerImLøsning(
                    value = mockTrengerInntekt().copy(
                        fnr = "fnr",
                        orgnr = "orgnr",
                        sykmeldingsperioder = sykmeldingsperioder(LocalDate.of(2022, 5, 1), LocalDate.of(2022, 5, 16)),
                        forespurtData = emptyList()
                    )
                )
            )
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        val løsning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        val inntektLøsning = objectMapper.readValue<InntektLøsning>(løsning.get(BEHOV_INNTEKT).toString())
        assertNull(inntektLøsning.value)
        assertNotNull(inntektLøsning.error)
        assertEquals("Klarte ikke hente inntekt", inntektLøsning.error?.melding)
    }

    @Test
    fun `skal publisere svar fra inntektskomponenten`() {
        val response = objectMapper.readValue<InntektskomponentResponse>("response.json".loadFromResources())
        every {
            runBlocking {
                inntektKlient.hentInntektListe(any(), any(), any(), any(), any(), any(), any())
            }
        } returns response
        val melding = mapOf(
            "@behov" to listOf(BEHOV_PDL, BEHOV_INNTEKT),
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "identitetsnummer" to "abc",
            Key.ORGNRUNDERENHET.str to "123456789",
            Key.SESSION.str to mapOf(
                BehovType.HENT_TRENGER_IM to HentTrengerImLøsning(
                    value = mockTrengerInntekt().copy(
                        fnr = "fnr",
                        orgnr = "orgnr",
                        sykmeldingsperioder = sykmeldingsperioder(LocalDate.of(2022, 5, 1), LocalDate.of(2022, 5, 16)),
                        forespurtData = emptyList()
                    )
                )
            )
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        val løsning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        val inntektLøsning = objectMapper.readValue<InntektLøsning>(løsning.get(BEHOV_INNTEKT).toString())
        assertNull(inntektLøsning.error)
        assertNotNull(inntektLøsning.value)
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
                Periode(
                    fom = LocalDate.of(2022, 4, 1),
                    tom = LocalDate.of(2022, 4, 20)
                ),
                Periode(
                    fom = LocalDate.of(2022, 3, 1),
                    tom = LocalDate.of(2022, 3, 20)
                )
            )
        )
        // Skal velge mars, dermed blir tremånedersperioden desember - februar
        val expectedFom = LocalDate.of(2021, 12, 1)
        val expectedTom = LocalDate.of(2022, 2, 28)
        assertEquals(expectedFom, inntektPeriode.fom)
        assertEquals(expectedTom, inntektPeriode.tom)
    }

    @Test
    fun `skal håndtere ulovlige verdier i sykmeldingsperiode`() {
        assertThrows(IllegalArgumentException::class.java, { finnInntektPeriode(null) }, "Skal aldri få null i sykmeldingperiode")
        assertThrows(IllegalArgumentException::class.java, { finnInntektPeriode(emptyList()) }, "Skal aldri få tom sykmeldingperiode")
    }

    @Test
    fun `skal slå sammen perioder som overlapper`() {
        val p1 = Periode(1.februar(2023), 5.februar(2023))
        val p2 = Periode(4.februar(2023), 19.februar(2023))
        val perioder = listOf(p2, p1)
        val sortertOgSlåttSammen = slåSammenPerioder(perioder)
        assertTrue(sortertOgSlåttSammen.size == 1)
        assertEquals(p1.fom, sortertOgSlåttSammen.get(0).fom)
        assertEquals(p2.tom, sortertOgSlåttSammen.get(0).tom)
    }

//    @Test
//    fun `Bruk første dato i siste sammenhengende sykmeldingsperiode`() {
//        val aar = 2023
//        val p1 = Periode(31.januar(aar), 2.februar(aar)) // 3 dager, så opphold 10 dager, så 2 dager -> mindre enn 16 dager arb.giver-periode, teller ikke!
//        val p2 = Periode(12.februar(aar), 14.februar(aar))
//        val p3 = Periode(26.februar(aar), 28.februar(aar)) // p3 + p4 er en sammenhengende periode større enn arb.giver-periode - 26/2 skal brukes som start!
//        val p4 = Periode(1.mars(aar), 20.mars(aar))
//        val sykmeldinger = listOf(p1, p2, p3, p4)
//    }

    @Test
    fun `skal finne riktig inntekt basert på sykmeldingsperioden som kommer i BEHOV`() {
        // TODO: Denne testen er litt grisete og tester mest egen mocke-logikk, men beholder foreløpig
        val svar = List(12) {
            lagInntektMaaned(YearMonth.now().minusMonths(it.toLong()), 1.0, "orgnr")
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

        val sykmeldingsperiode = sykmeldingsperioder(LocalDate.of(2022, 12, 1), LocalDate.of(2022, 12, 30))
        val melding = mapOf(
            "@behov" to listOf(BEHOV_PDL, BEHOV_INNTEKT),
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "identitetsnummer" to "fnr",
            Key.ORGNRUNDERENHET.str to "orgnr",
            Key.SESSION.str to mapOf(
                BehovType.HENT_TRENGER_IM to HentTrengerImLøsning(
                    value = mockTrengerInntekt().copy(
                        fnr = "fnr",
                        orgnr = "orgnr",
                        sykmeldingsperioder = sykmeldingsperiode,
                        forespurtData = emptyList()
                    )
                )
            )
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        val løsning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        val inntektLøsning = objectMapper.readValue<InntektLøsning>(løsning.get(BEHOV_INNTEKT).toString())
        assertNull(inntektLøsning.error)
        assertEquals(3, inntektLøsning.value!!.historisk.size)
        assertEquals(YearMonth.of(2022, 11), inntektLøsning.value!!.historisk[0].maanedsnavn)
        assertEquals(YearMonth.of(2022, 10), inntektLøsning.value!!.historisk[1].maanedsnavn)
        assertEquals(YearMonth.of(2022, 9), inntektLøsning.value!!.historisk[2].maanedsnavn)
    }

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
