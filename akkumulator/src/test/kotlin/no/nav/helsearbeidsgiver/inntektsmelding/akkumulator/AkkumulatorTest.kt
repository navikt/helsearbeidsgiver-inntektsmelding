package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Test
import java.util.UUID

internal class AkkumulatorTest {

    private val rapid = TestRapid()
    private val redisStore = mockk<RedisStore>()
    private var akkumulator: Akkumulator
    private val timeout = 600L

    internal val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())

    init {
        akkumulator = Akkumulator(rapid, redisStore, timeout)
    }

    @Test
    fun `skal behandle komplett løsning`() {
        every { redisStore.get("uuid_BrregLøser") } returns "Brreg-001"
        every { redisStore.get("uuid_PdlLøser") } returns "Pdl-001"
        every { redisStore.set(any(), any(), timeout) } returns Unit
        val melding = mapOf(
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "@behov" to listOf("PdlLøser", "BrregLøser"),
            "@løsning" to mapOf(
                "BrregLøser" to "dummy"
            )
        )
        val json = "{\"PdlLøser\":\"Pdl-001\",\"BrregLøser\":\"Brreg-001\"}"
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))

        verify(exactly = 1) {
            redisStore.set("uuid_BrregLøser", "dummy", timeout)
        }
        verify(exactly = 1) {
            redisStore.set("uuid", json, timeout)
        }
    }

    @Test
    fun `skal behandle ukomplett løsning`() {
        every { redisStore.get("uuid_BrregLøser") } returns "Brreg-001"
        every { redisStore.get("uuid_PdlLøser") } returns ""
        every { redisStore.set(any(), any(), timeout) } returns Unit
        val melding = mapOf(
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "@behov" to listOf("PdlLøser", "BrregLøser"),
            "@løsning" to mapOf(
                "BrregLøser" to ""
            )
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        verify(exactly = 0) {
            redisStore.set("uuid", any(), any())
        }
    }
}
