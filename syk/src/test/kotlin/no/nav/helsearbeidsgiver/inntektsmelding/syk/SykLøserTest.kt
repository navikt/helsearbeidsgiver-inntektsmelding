@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.syk

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.SykLøsning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SykLøserTest {

    private val rapid = TestRapid()
    private var sykLøser: SykLøser
    private val BEHOV_PDL = BehovType.FULLT_NAVN.toString()
    private val BEHOV_INNTEKT = BehovType.INNTEKT.toString()
    private val BEHOV_SYK = BehovType.SYK.toString()
    internal val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())

    init {
        sykLøser = SykLøser(rapid)
    }

    @BeforeEach
    fun beforeEach() {
        rapid.reset()
    }

    @Test
    fun `skal ikke respondere når det ikke er behov`() {
        rapid.sendTestMessage(
            objectMapper.writeValueAsString(
                mapOf(
                    "@behov" to listOf(BEHOV_PDL),
                    "@id" to UUID.randomUUID(),
                    "uuid" to "uuid",
                    "identitetsnummer" to "abc"
                )
            )
        )
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `skal kreve at identitetsnummer er satt`() {
        rapid.sendTestMessage(
            objectMapper.writeValueAsString(
                mapOf(
                    "@behov" to listOf(BEHOV_PDL),
                    "@id" to UUID.randomUUID(),
                    "uuid" to "uuid"
                )
            )
        )
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `skal kreve at id er satt`() {
        rapid.sendTestMessage(
            objectMapper.writeValueAsString(
                mapOf(
                    "@behov" to listOf(BEHOV_PDL),
                    "uuid" to "uuid",
                    "identitetsnummer" to "abc"
                )
            )
        )
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `skal håndtere når man får hentet data`() {
        val melding = mapOf(
            "@behov" to listOf(BEHOV_PDL, BEHOV_INNTEKT, BEHOV_SYK),
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "identitetsnummer" to "abc"
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        val løsning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        val inntektLøsning = objectMapper.readValue<SykLøsning>(løsning.get(BEHOV_SYK).toString())
        assertNotNull(inntektLøsning.value)
        assertNull(inntektLøsning.error)
    }

    @Test
    fun `skal håndtere når man ikke får hentet data`() {
        val melding = mapOf(
            "@behov" to listOf(BEHOV_PDL, BEHOV_INNTEKT, BEHOV_SYK),
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "identitetsnummer" to "000000000"
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        val løsning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        val inntektLøsning = objectMapper.readValue<SykLøsning>(løsning.get(BEHOV_SYK).toString())
        assertNull(inntektLøsning.value)
        assertNotNull(inntektLøsning.error)
        assertEquals("Klarte ikke hente syk", inntektLøsning.error?.melding)
    }
}
