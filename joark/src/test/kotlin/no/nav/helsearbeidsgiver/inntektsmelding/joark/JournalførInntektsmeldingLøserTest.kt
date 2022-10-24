package no.nav.helsearbeidsgiver.inntektsmelding.joark

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.JournalpostLøsning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

internal class JournalførInntektsmeldingLøserTest {

    private val rapid = TestRapid()
    private var løser: JournalførInntektsmeldingLøser
    private val BEHOV = BehovType.JOURNALFOER.name
    internal val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())

    init {
        løser = JournalførInntektsmeldingLøser(rapid)
    }

    @Test
    fun `skal journalføre når gyldige data`() {
        val løsning = sendMessage(
            mapOf(
                "@behov" to listOf(BEHOV),
                "@id" to UUID.randomUUID(),
                "uuid" to "uuid",
                "identitetsnummer" to "123",
                "orgnrUnderenhet" to "abc",
                "inntektsmelding" to "xyz"
            )
        )
        assertEquals("jp-123", løsning.value)
    }

    @Test
    fun `skal håndtere ukjente feil`() {
        val løsning = sendMessage(
            mapOf(
                "@behov" to listOf(BEHOV),
                "@id" to UUID.randomUUID(),
                "uuid" to "uuid",
                "identitetsnummer" to "000",
                "orgnrUnderenhet" to "abc",
                "inntektsmelding" to "xyz"
            )
        )
        assertNotNull(løsning.error)
    }

    fun sendMessage(mapOf: Map<String, Any>): JournalpostLøsning {
        rapid.reset()
        rapid.sendTestMessage(
            objectMapper.writeValueAsString(
                mapOf
            )
        )
        val løsning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        return objectMapper.readValue<JournalpostLøsning>(løsning.get(BEHOV).toString())
    }
}
