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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.utils.log.logger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

class IkkeBlokkerendeVirksomhetLøserTest {

    private val rapid = TestRapid()
    private var løser: IkkeBlokkerendeVirksomhetLøser
    private val BEHOV = BehovType.VIRKSOMHET.name
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())
    private val brregClient = mockk<BrregClient>()
    private val ORGNR = "orgnr-1"
    private val VIRKSOMHET_NAVN = "Norge AS"
    private val DELAY = 1000L
    init {
        løser = IkkeBlokkerendeVirksomhetLøser(rapid, brregClient, false, DELAY)
    }

    private suspend fun sendMessage(packet: Map<String, Any>): VirksomhetLøsning {
        rapid.reset()
        rapid.sendTestMessage(
            objectMapper.writeValueAsString(
                packet
            )
        )
        var counter = 0
        while (rapid.inspektør.size == 0) {
            counter++
            // med runtest ignoreres vanlig delay()
            withContext(Dispatchers.Default) {
                delay(50.milliseconds)
                if (counter % 100 == 0) {
                    logger().info("Venter på svar...")
                }
            }
        }
        val losning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        return objectMapper.readValue<VirksomhetLøsning>(losning.get(BEHOV).toString())
    }

    @Test
    fun `skal håndtere at klient feiler`() = runTest {
        coEvery {
            brregClient.hentVirksomhetNavn(any())
        } returns null
        launch {
            val løsning = sendMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.TRENGER_REQUESTED.name,
                    Key.FORESPOERSEL_ID.str to UUID.randomUUID(),
                    "@behov" to listOf(BEHOV),
                    "@id" to UUID.randomUUID(),
                    "uuid" to "uuid",
                    DataFelt.ORGNRUNDERENHET.str to ORGNR
                )
            )
            assertEquals("Ugyldig virksomhet $ORGNR", løsning.error?.melding)
        }
    }

    @Test
    fun `skal returnere løsning når gyldige data`() = runTest {
        coEvery {
            brregClient.hentVirksomhetNavn(any())
        } returns VIRKSOMHET_NAVN
        val løsning = sendMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.TRENGER_REQUESTED.name,
                Key.FORESPOERSEL_ID.str to UUID.randomUUID(),
                "@behov" to listOf(BEHOV),
                "@id" to UUID.randomUUID(),
                "uuid" to "uuid",
                DataFelt.ORGNRUNDERENHET.str to ORGNR
            )
        )
        assertEquals(VIRKSOMHET_NAVN, løsning.value)
    }

    @Test
    fun `skal håndtere ukjente feil`() = runTest {
        val løsning = sendMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.TRENGER_REQUESTED.name,
                Key.FORESPOERSEL_ID.str to UUID.randomUUID(),
                "@behov" to listOf(BEHOV),
                "@id" to UUID.randomUUID(),
                DataFelt.ORGNRUNDERENHET.str to ORGNR
            )
        )
        assertNotNull(løsning.error)
    }
}
