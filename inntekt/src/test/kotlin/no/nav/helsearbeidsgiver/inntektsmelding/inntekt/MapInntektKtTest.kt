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

    @Test
    fun `skal mappe riktig inntekter`() {
        val response = objectMapper.readValue<InntektskomponentResponse>("response.json".loadFromResources())
        val inntekt = mapInntekt(response)
        assertEquals(123.0, inntekt.historisk.get(0).inntekt)
        assertEquals(YearMonth.of(2020, 4), inntekt.historisk.get(0).maanedsnavn)
        assertEquals(444.0, inntekt.historisk.get(1).inntekt)
        assertEquals(YearMonth.of(2020, 4), inntekt.historisk.get(1).maanedsnavn)
    }

    @Test
    fun `skal mappe riktig total`() {
        val response = objectMapper.readValue<InntektskomponentResponse>("response.json".loadFromResources())
        val inntekt = mapInntekt(response)
        assertEquals(567.0, inntekt.bruttoInntekt)
    }
}
