@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivException
import no.nav.helsearbeidsgiver.dokarkiv.OpprettJournalpostResponse
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.JournalpostLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument.MockInntektsmeldingDokument
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
    private val dokArkivClient = mockk<DokArkivClient>()

    init {
        løser = JournalførInntektsmeldingLøser(rapid, dokArkivClient)
    }

    fun sendMessage(packet: Map<String, Any>): JournalpostLøsning {
        rapid.reset()
        rapid.sendTestMessage(
            objectMapper.writeValueAsString(
                packet
            )
        )
        val losning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        return objectMapper.readValue<JournalpostLøsning>(losning.get(BEHOV).toString())
    }

    @Test
    fun `skal håndtere at dokarkiv feiler`() {
        coEvery {
            dokArkivClient.opprettJournalpost(any(), any(), any())
        } throws DokArkivException(Exception(""))
        val løsning = sendMessage(
            mapOf(
                "@behov" to listOf(BEHOV),
                "@id" to UUID.randomUUID(),
                "uuid" to "uuid",
                "identitetsnummer" to "000",
                Key.ORGNRUNDERENHET.str to "abc",
                Key.INNTEKTSMELDING_DOKUMENT.str to MockInntektsmeldingDokument()
            )
        )
        assertEquals("Kall mot dokarkiv feilet", løsning.error?.melding)
    }

    @Test
    fun `skal journalføre når gyldige data`() {
        coEvery {
            dokArkivClient.opprettJournalpost(any(), any(), any())
        } returns OpprettJournalpostResponse("jp-123", journalpostFerdigstilt = true, "FERDIGSTILT", "", emptyList())
        val løsning = sendMessage(
            mapOf(
                "@behov" to listOf(BEHOV),
                "@id" to UUID.randomUUID(),
                "uuid" to "uuid",
                Key.INNTEKTSMELDING_DOKUMENT.str to MockInntektsmeldingDokument(),
                "session" to mapOf(
                    "Virksomhet" to mapOf(
                        "value" to "Norge AS"
                    )
                )
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
                Key.ORGNRUNDERENHET.str to "abc",
                Key.INNTEKTSMELDING_DOKUMENT.str to "xyz"
            )
        )
        assertNotNull(løsning.error)
    }
}
