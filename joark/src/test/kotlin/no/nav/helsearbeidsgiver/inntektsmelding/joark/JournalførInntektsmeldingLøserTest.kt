@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivStatusException
import no.nav.helsearbeidsgiver.dokarkiv.OpprettJournalpostResponse
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.JournalpostLøsning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

internal class JournalførInntektsmeldingLøserTest {

    private val rapid = TestRapid()
    private var løser: JournalførInntektsmeldingLøser
    private val BEHOV = BehovType.JOURNALFOER.name
    private val dokarkivClient = mockk<DokArkivClient>()
    internal val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())

    init {
        løser = JournalførInntektsmeldingLøser(rapid, dokarkivClient)
    }

    fun sendMessage(packet: Map<String, Any>): JournalpostLøsning {
        rapid.reset()
        rapid.sendTestMessage(
            objectMapper.writeValueAsString(
                packet
            )
        )
        val losning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        return objectMapper.readValue(losning.get(BEHOV).toString())
    }

    @Test
    fun `skal journalføre når gyldige data`() {
        every {
            runBlocking {
                dokarkivClient.opprettJournalpost(any(), any(), any())
            }
        } returns OpprettJournalpostResponse("jp-123", true, "status", melding = "", emptyList())
        val løsning = sendMessage(PACKET_VALID)
        assertEquals("jp-123", løsning.value)
    }

    @Test
    fun `skal håndtere feil format i inntektsmelding`() {
        val løsning = sendMessage(PACKET_INVALID)
        assertNotNull(løsning.error)
        assertEquals("Feil format i inntektsmelding", løsning.error?.melding)
    }

    @Test
    fun `skal håndtere at journalpost feiler`() {
        every {
            runBlocking {
                dokarkivClient.opprettJournalpost(any(), any(), any())
            }
        } throws DokArkivStatusException(500, "Feil!")
        val løsning = sendMessage(PACKET_VALID)
        assertNotNull(løsning.error)
        assertEquals("Klarte ikke journalføre", løsning.error?.melding)
    }
}
