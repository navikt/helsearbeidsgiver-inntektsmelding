package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helsearbeidsgiver.felles.test.resource.readResource
import no.nav.helsearbeidsgiver.inntekt.InntektskomponentResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.YearMonth

private const val ORGNR = "123456785"

class MapInntektKtTest {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())

    @Test
    fun `skal mappe riktig inntekter`() {
        val response = lagRespons("response.json")
        val inntekt = mapInntekt(response, ORGNR)

        assertEquals(1, inntekt.historisk.size)
        assertEquals(567.0, inntekt.gjennomsnitt())
        assertEquals(567.0, inntekt.total())
        assertEquals(YearMonth.of(2020, 4), inntekt.historisk[0].maaned)
    }

    @Test
    fun `skal mappe flere maaneder`() {
        val response = lagRespons("response_flere_mnd.json")
        val inntekt = mapInntekt(response, ORGNR)

        assertEquals(2, inntekt.historisk.size)
        assertEquals(1134.0, inntekt.total())
    }

    @Test
    fun `skal mappe riktig total`() {
        val response = lagRespons("response.json")
        val inntekt = mapInntekt(response, ORGNR)

        assertEquals(567.0, inntekt.total())
    }

    @Test
    fun `Returnerer tom respons hvis orgnr ikke er satt`() {
        val response = lagRespons("response.json")
        val inntekt = mapInntekt(response, "")

        assertEquals(0.0, inntekt.total())
        assertEquals(0, inntekt.historisk.size)
    }

    @Test
    fun `skal filtrere inntekt på innsendt orgnr`() {
        val response = lagRespons("inntekt_fra_flere_arbeidsgivere.json")
        val inntekt = mapInntekt(response, ORGNR)

        assertEquals(690.0, inntekt.total())

        val inntekt2 = mapInntekt(response, "987654321")

        assertEquals(1000.0, inntekt2.total())
    }

    @Test
    fun `test floatfeil itotal`() {
        val response = lagRespons("kjip_total.json")
        // 10 * 0.1 bør bli 1.0, men blir feil om man benytter Double i utregning..
        val inntekt = mapInntekt(response, ORGNR)

        assertEquals(1.0, inntekt.total())
    }

    private fun lagRespons(filnavn: String): InntektskomponentResponse = objectMapper.readValue(filnavn.readResource())
}
