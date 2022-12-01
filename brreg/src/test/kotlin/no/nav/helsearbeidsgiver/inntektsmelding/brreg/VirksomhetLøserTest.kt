@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.brreg

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
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VirksomhetLøserTest {

    private val rapid = TestRapid()
    private var løser: VirksomhetLøser
    private val BEHOV = BehovType.VIRKSOMHET.name
    internal val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())
    private val brregClient = mockk<BrregClient>()
    private val ORGNR = "orgnr-1"
    private val VIRKSOMHET_NAVN = "Norge AS"

    init {
        løser = VirksomhetLøser(rapid, brregClient, false)
    }

    fun sendMessage(packet: Map<String, Any>): VirksomhetLøsning {
        rapid.reset()
        rapid.sendTestMessage(
            objectMapper.writeValueAsString(
                packet
            )
        )
        val losning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        return objectMapper.readValue<VirksomhetLøsning>(losning.get(BEHOV).toString())
    }

    @Test
    fun `skal håndtere at klient feiler`() {
        coEvery {
            brregClient.hentVirksomhetNavn(any())
        } returns null
        val løsning = sendMessage(
            mapOf(
                "@behov" to listOf(BEHOV),
                "@id" to UUID.randomUUID(),
                "uuid" to "uuid",
                "orgnrUnderenhet" to ORGNR
            )
        )
        assertEquals("Ugyldig virksomhet $ORGNR", løsning.error?.melding)
    }

    @Test
    fun `skal returnere løsning når gyldige data`() {
        coEvery {
            brregClient.hentVirksomhetNavn(any())
        } returns VIRKSOMHET_NAVN
        val løsning = sendMessage(
            mapOf(
                "@behov" to listOf(BEHOV),
                "@id" to UUID.randomUUID(),
                "uuid" to "uuid",
                "orgnrUnderenhet" to ORGNR
            )
        )
        assertEquals(VIRKSOMHET_NAVN, løsning.value)
    }

    @Test
    fun `skal håndtere ukjente feil`() {
        val løsning = sendMessage(
            mapOf(
                "@behov" to listOf(BEHOV),
                "@id" to UUID.randomUUID(),
                "orgnrUnderenhet" to ORGNR
            )
        )
        assertNotNull(løsning.error)
    }
}
