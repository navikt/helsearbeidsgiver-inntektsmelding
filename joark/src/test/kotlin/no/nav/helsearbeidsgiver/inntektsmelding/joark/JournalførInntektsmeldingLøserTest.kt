@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.contains
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivException
import no.nav.helsearbeidsgiver.dokarkiv.OpprettJournalpostResponse
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument.mockInntektsmeldingDokument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class JournalførInntektsmeldingLøserTest {

    private val rapid = TestRapid()
    private var løser: JournalførInntektsmeldingLøser
    private val BEHOV = BehovType.JOURNALFOER.name
    private val F_ID = UUID.randomUUID().toString()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())
    private val dokArkivClient = mockk<DokArkivClient>()

    init {
        løser = JournalførInntektsmeldingLøser(rapid, dokArkivClient)
    }

    private fun sendMessage(packet: Map<String, Any>) {
        rapid.reset()
        rapid.sendTestMessage(
            objectMapper.writeValueAsString(
                packet
            )
        )
    }

    private fun retrieveMessage(index: Int): JsonNode {
        return rapid.inspektør.message(index)
    }

    @Test
    fun `skal håndtere at dokarkiv feiler`() {
        coEvery {
            dokArkivClient.opprettJournalpost(any(), any(), any())
        } throws DokArkivException(Exception(""))
        sendMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT,
                Key.BEHOV.str to BehovType.JOURNALFOER.name,
                Key.ID.str to UUID.randomUUID(),
                Key.UUID.str to "uuid",
                DataFelt.INNTEKTSMELDING_DOKUMENT.str to mockInntektsmeldingDokument(),
                Key.FORESPOERSEL_ID.str to F_ID
            )
        )
        val message = retrieveMessage(0)
        assert(message.contains(Key.FAIL.str).and(!message.contains(DataFelt.INNTEKTSMELDING_DOKUMENT.str)))
        val fail = objectMapper.treeToValue(message.path(Key.FAIL.str), Fail::class.java)
        assertEquals("Kall mot dokarkiv feilet", fail.feilmelding)
    }

    @Test
    fun `skal journalføre når gyldige data`() {
        coEvery {
            dokArkivClient.ferdigstillJournalpost(any(), any())
        } returns "jp-123"
        coEvery {
            dokArkivClient.opprettJournalpost(any(), any(), any())
        } returns OpprettJournalpostResponse("jp-123", journalpostFerdigstilt = false, "FERDIGSTILT", "", emptyList())
        val løsning = sendMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT,
                Key.FORESPOERSEL_ID.str to F_ID,
                "@behov" to BehovType.JOURNALFOER.name,
                "@id" to UUID.randomUUID(),
                "uuid" to "uuid",
                DataFelt.INNTEKTSMELDING_DOKUMENT.str to mockInntektsmeldingDokument(),
                "session" to mapOf(
                    "Virksomhet" to mapOf(
                        "value" to "Norge AS"
                    )
                )
            )
        )

        val msg2 = rapid.inspektør.message(0)
        assertEquals(BehovType.LAGRE_JOURNALPOST_ID.name, msg2.path(Key.BEHOV.str).asText())
        assertEquals("jp-123", msg2.path(Key.JOURNALPOST_ID.str).asText())
        assertEquals("uuid", msg2.path(Key.UUID.str).asText())
        assertEquals(F_ID, msg2.path(Key.FORESPOERSEL_ID.str).asText())
    }

    @Test
    fun `skal håndtere ukjente feil`() {
        sendMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT,
                Key.BEHOV.str to BEHOV,
                Key.ID.str to UUID.randomUUID(),
                Key.UUID.str to "uuid",
                "identitetsnummer" to "000",
                DataFelt.ORGNRUNDERENHET.str to "abc",
                DataFelt.INNTEKTSMELDING_DOKUMENT.str to "xyz",
                Key.FORESPOERSEL_ID.str to F_ID
            )
        )
        val fail = objectMapper.treeToValue(retrieveMessage(0).get(Key.FAIL.str), Fail::class.java)
        assertTrue(fail.feilmelding.isNotEmpty())
    }
}
