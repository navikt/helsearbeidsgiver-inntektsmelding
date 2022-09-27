@file:Suppress("NonAsciiCharacters")

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
import no.nav.helsearbeidsgiver.felles.Behov
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Løsning
import org.junit.jupiter.api.Test
import java.util.UUID

internal class AkkumulatorTest {

    private val rapid = TestRapid()
    private val redisStore = mockk<RedisStore>()
    private var akkumulator: Akkumulator
    private val timeout = 600L

    private val BEHOV_PDL = Behov.FULLT_NAVN.toString()
    private val BEHOV_BRREG = Behov.VIRKSOMHET.toString()

    private val UUID_BRREG = "uuid_" + BEHOV_BRREG
    private val UUID_PDL = "uuid_" + BEHOV_PDL

    val LØSNING_FEIL = Løsning(errors = listOf(Feilmelding("Fikk 500")))
    val LØSNING_OK = Løsning(value = "abc")
    val PDL_OK = Løsning(value = "xyz")

    internal val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())

    init {
        akkumulator = Akkumulator(rapid, redisStore, timeout)
    }

    @Test
    fun `skal lagre verdi`() {
        every { redisStore.get(UUID_PDL) } returns objectMapper.writeValueAsString(PDL_OK)
        every { redisStore.get(UUID_BRREG) } returns ""
        every { redisStore.set(any(), any(), timeout) } returns Unit
        val melding = mapOf(
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "@behov" to listOf(BEHOV_PDL, BEHOV_BRREG),
            "@løsning" to mapOf(
                BEHOV_PDL to PDL_OK
            )
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        verify(exactly = 1) {
            redisStore.set(UUID_PDL, objectMapper.writeValueAsString(PDL_OK), timeout)
        }
        verify(exactly = 0) {
            redisStore.set("uuid", any(), any())
        }
    }

    @Test
    fun `skal behandle errors i løsninger`() {
        every { redisStore.get(UUID_BRREG) } returns objectMapper.writeValueAsString(LØSNING_FEIL)
        every { redisStore.get(UUID_PDL) } returns objectMapper.writeValueAsString(PDL_OK)
        every { redisStore.set(any(), any(), timeout) } returns Unit
        val melding = mapOf(
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "@behov" to listOf(BEHOV_PDL, BEHOV_BRREG),
            "@løsning" to mapOf(
                BEHOV_BRREG to LØSNING_FEIL
            )
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        verify(exactly = 1) {
            redisStore.set(UUID_BRREG, objectMapper.writeValueAsString(LØSNING_FEIL), timeout)
        }
        verify(exactly = 1) {
            redisStore.set("uuid", any(), any())
        }
    }

    @Test
    fun `skal behandle komplett løsning`() {
        every { redisStore.get(UUID_BRREG) } returns objectMapper.writeValueAsString(LØSNING_OK)
        every { redisStore.get(UUID_PDL) } returns objectMapper.writeValueAsString(PDL_OK)
        every { redisStore.set(any(), any(), timeout) } returns Unit
        val melding = mapOf(
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "@behov" to listOf(BEHOV_PDL, BEHOV_BRREG),
            "@løsning" to mapOf(
                BEHOV_BRREG to LØSNING_OK,
                BEHOV_PDL to PDL_OK
            )
        )
        val toStk = mapOf(
            BEHOV_PDL to PDL_OK,
            BEHOV_BRREG to LØSNING_OK
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        verify(exactly = 1) {
            redisStore.set(UUID_BRREG, objectMapper.writeValueAsString(LØSNING_OK), timeout)
        }
        verify(exactly = 1) {
            redisStore.set(UUID_PDL, objectMapper.writeValueAsString(PDL_OK), timeout)
        }
        verify(exactly = 1) {
            redisStore.set("uuid", objectMapper.writeValueAsString(toStk), timeout)
        }
    }

    @Test
    fun `skal behandle ukomplett løsning`() {
        every { redisStore.get(UUID_BRREG) } returns objectMapper.writeValueAsString(LØSNING_OK)
        every { redisStore.get(UUID_PDL) } returns ""
        every { redisStore.set(any(), any(), timeout) } returns Unit
        val melding = mapOf(
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "@behov" to listOf(BEHOV_PDL, BEHOV_BRREG),
            "@løsning" to mapOf(
                BEHOV_BRREG to ""
            )
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        verify(exactly = 1) {
            redisStore.set(UUID_BRREG, any(), timeout)
        }
        verify(exactly = 0) {
            redisStore.set("uuid", any(), any())
        }
    }
}
