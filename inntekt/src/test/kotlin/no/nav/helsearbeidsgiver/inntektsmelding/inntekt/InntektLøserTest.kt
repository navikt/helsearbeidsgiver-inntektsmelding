@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

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
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.InntektLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.test.resource.readResource
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.inntekt.InntektskomponentResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

internal class InntektLøserTest {

    private val rapid = TestRapid()
    private var inntektLøser: InntektLøser
    private var inntektKlient: InntektKlient
    private val BEHOV_PDL = BehovType.FULLT_NAVN.toString()
    private val BEHOV_INNTEKT = BehovType.INNTEKT.toString()
    internal val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())

    init {
        inntektKlient = mockk<InntektKlient>()
        inntektLøser = InntektLøser(rapid, inntektKlient)
    }

    @BeforeEach
    fun beforeEach() {
        rapid.reset()
    }

    @Test
    fun `skal håndtere feil mot inntektskomponenten`() {
        every {
            runBlocking {
                inntektKlient.hentInntektListe(any(), any(), any(), any(), any(), any(), any())
            }
        } throws RuntimeException()
        val melding = mapOf(
            "@behov" to listOf(BEHOV_PDL, BEHOV_INNTEKT),
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "identitetsnummer" to "abc",
            Key.ORGNRUNDERENHET.str to "123456789"
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        val løsning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        val inntektLøsning = objectMapper.readValue<InntektLøsning>(løsning.get(BEHOV_INNTEKT).toString())
        assertNull(inntektLøsning.value)
        assertNotNull(inntektLøsning.error)
        assertEquals("Klarte ikke hente inntekt", inntektLøsning.error?.melding)
    }

    @Test
    fun `skal publisere svar fra inntektskomponenten`() {
        val response = objectMapper.readValue<InntektskomponentResponse>("response.json".readResource())
        every {
            runBlocking {
                inntektKlient.hentInntektListe(any(), any(), any(), any(), any(), any(), any())
            }
        } returns response
        val melding = mapOf(
            "@behov" to listOf(BEHOV_PDL, BEHOV_INNTEKT),
            "@id" to UUID.randomUUID(),
            "uuid" to "uuid",
            "identitetsnummer" to "abc",
            Key.ORGNRUNDERENHET.str to "123456789"
        )
        rapid.sendTestMessage(objectMapper.writeValueAsString(melding))
        val løsning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        val inntektLøsning = objectMapper.readValue<InntektLøsning>(løsning.get(BEHOV_INNTEKT).toString())
        assertNull(inntektLøsning.error)
        assertNotNull(inntektLøsning.value)
    }
}
