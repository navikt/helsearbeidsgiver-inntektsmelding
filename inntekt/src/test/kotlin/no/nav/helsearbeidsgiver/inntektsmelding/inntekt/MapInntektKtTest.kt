package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helsearbeidsgiver.inntekt.InntektskomponentResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.YearMonth

internal class MapInntektKtTest {

    internal val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())

    private val ORGNR = "123456785"

    @Test
    fun `skal mappe riktig inntekter`() {
        val response = objectMapper.readValue<InntektskomponentResponse>("response.json".loadFromResources())
        val inntekt = mapInntekt(response, ORGNR)
        assertEquals(123.0, inntekt.historisk.get(0).inntekt)
        assertEquals(YearMonth.of(2020, 4), inntekt.historisk.get(0).maanedsnavn)
        assertEquals(444.0, inntekt.historisk.get(1).inntekt)
        assertEquals(YearMonth.of(2020, 4), inntekt.historisk.get(1).maanedsnavn)
    }

    @Test
    fun `skal mappe flere maaneder`() {
        val response = objectMapper.readValue<InntektskomponentResponse>("response_flere_mnd.json".loadFromResources())
        val inntekt = mapInntekt(response, ORGNR)
        assertEquals(4, inntekt.historisk.size)
        assertEquals(1134.0, inntekt.total)
    }

    @Test
    fun `skal mappe riktig total`() {
        val response = objectMapper.readValue<InntektskomponentResponse>("response.json".loadFromResources())
        val inntekt = mapInntekt(response, ORGNR)
        assertEquals(567.0, inntekt.total)
    }

    @Test
    fun `Returnerer tom respons hvis orgnr ikke er satt`() {
        val response = objectMapper.readValue<InntektskomponentResponse>("response.json".loadFromResources())
        val inntekt = mapInntekt(response, "")
        assertEquals(0.0, inntekt.total)
        assertEquals(0, inntekt.historisk.size)
    }

    @Test
    fun `skal filtrere inntekt på innsendt orgnr`() {
        val response = objectMapper.readValue<InntektskomponentResponse>("inntekt_fra_flere_arbeidsgivere.json".loadFromResources())
        val inntekt = mapInntekt(response, ORGNR)
        assertEquals(690.0, inntekt.total)
        val inntekt2 = mapInntekt(response, "987654321")
        assertEquals(1000.0, inntekt2.total)
    }

    @Test
    fun test_float_feil_i_total() {
        val response = objectMapper.readValue<InntektskomponentResponse>("kjip_total.json".loadFromResources())
        // 10 * 0.1 bør bli 1.0, men blir feil om man benytter Double i utregning..
        val inntekt = mapInntekt(response, ORGNR)
        assertEquals(1.0, inntekt.total)
    }
}
