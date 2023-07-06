@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.brreg

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
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Data
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Message
import no.nav.helsearbeidsgiver.felles.test.json.toDomeneMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

class VirksomhetLøserTest {

    private val rapid = TestRapid()
    private var løser: VirksomhetLøser
    private val BEHOV = BehovType.VIRKSOMHET.name
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())
    private val brregClient = mockk<BrregClient>()
    private val ORGNR = "orgnr-1"
    private val VIRKSOMHET_NAVN = "Norge AS"

    init {
        løser = VirksomhetLøser(rapid, brregClient, false)
    }

    private fun sendMessage(packet: Map<String, Any>): Message {
        rapid.reset()
        rapid.sendTestMessage(
            objectMapper.writeValueAsString(
                packet
            )
        )

        val response: JsonNode = rapid.inspektør.message(0)
        return response.toDomeneMessage {
            it.interestedIn(DataFelt.VIRKSOMHET)
        }
    }

    @Test
    fun `skal håndtere at klient feiler`() {
        coEvery {
            brregClient.hentVirksomhetNavn(any())
        } returns null
        val message = sendMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.TRENGER_REQUESTED.name,
                "@behov" to BEHOV,
                "@id" to UUID.randomUUID(),
                "uuid" to "uuid",
                DataFelt.ORGNRUNDERENHET.str to ORGNR
            )
        )
        assertEquals("Ugyldig virksomhet $ORGNR", (message as Fail).feilmelding)
    }

    @Test
    fun `skal returnere løsning når gyldige data`() {
        coEvery {
            brregClient.hentVirksomhetNavn(any())
        } returns VIRKSOMHET_NAVN
        val data: Data = sendMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.TRENGER_REQUESTED.name,
                "@behov" to BEHOV,
                "@id" to UUID.randomUUID(),
                "uuid" to "uuid",
                DataFelt.ORGNRUNDERENHET.str to ORGNR
            )
        ) as Data
        assertEquals(VIRKSOMHET_NAVN, data[DataFelt.VIRKSOMHET].asText())
    }

    @Test
    fun `skal håndtere ukjente feil`() {
        val fail = sendMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.TRENGER_REQUESTED.name,
                "@behov" to BEHOV,
                "@id" to UUID.randomUUID(),
                DataFelt.ORGNRUNDERENHET.str to ORGNR
            )
        ) as Fail
        assertNotNull(fail.feilmelding)
    }
}
