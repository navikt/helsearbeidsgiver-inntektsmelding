@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.egenmelding

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EgenmeldingLøsning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

internal class EgenmeldingLøserTest {

    private val rapid = TestRapid()
    private val IKKE_BEHOV = BehovType.FULLT_NAVN.toString()
    private val BEHOV = BehovType.EGENMELDING.toString()
    internal val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())

    init {
        EgenmeldingLøser(rapid)
    }

    @Test
    fun `skal ikke respondere når det ikke er behov`() {
        sendMessage(
            mapOf(
                "@behov" to listOf(IKKE_BEHOV),
                "@id" to UUID.randomUUID(),
                "uuid" to "uuid",
                "identitetsnummer" to "abc"
            )
        )
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `skal kreve at identitetsnummer er satt`() {
        sendMessage(
            mapOf(
                "@behov" to listOf(BEHOV),
                "@id" to UUID.randomUUID(),
                "uuid" to "uuid"
            )
        )
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `skal kreve at løsning ikke er satt`() {
        sendMessage(
            mapOf(
                "@behov" to listOf(BEHOV),
                "uuid" to "uuid",
                "identitetsnummer" to "abc",
                "@løsning" to ""
            )
        )
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `skal håndtere når man får hentet data`() {
        sendMessage(
            mapOf(
                "@behov" to listOf(BEHOV),
                "@id" to UUID.randomUUID(),
                "uuid" to "uuid",
                "identitetsnummer" to "abc"
            )
        )
        val løsning = hentLøsning()
        assertNotNull(løsning.value)
        assertNull(løsning.error)
    }

    @Test
    fun `skal håndtere når man ikke får hentet data`() {
        sendMessage(
            mapOf(
                "@behov" to listOf(BEHOV),
                "@id" to UUID.randomUUID(),
                "uuid" to "uuid",
                "identitetsnummer" to "000000000"
            )
        )
        val løsning = hentLøsning()
        assertNull(løsning.value)
        assertNotNull(løsning.error)
        assertEquals("Klarte ikke hente egenmelding", løsning.error?.melding)
    }

    fun sendMessage(mapOf: Map<String, Any>) {
        rapid.reset()
        rapid.sendTestMessage(
            objectMapper.writeValueAsString(
                mapOf
            )
        )
    }

    fun hentLøsning(): EgenmeldingLøsning {
        val løsning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        return objectMapper.readValue<EgenmeldingLøsning>(løsning.get(BEHOV).toString())
    }
}
