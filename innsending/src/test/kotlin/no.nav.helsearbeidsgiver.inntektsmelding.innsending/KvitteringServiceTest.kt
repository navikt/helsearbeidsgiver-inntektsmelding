package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled // Må starte lokal redis for å kjøre denne
class KvitteringServiceTest {
    private val redisStore = RedisStore("redis://localhost:6379/0")

    private val testRapid: TestRapid = TestRapid()

    val foresporselid = "abc"
    val transactionId = "123456"

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `kvitteringServiceTest`() {
        val service = KvitteringService(testRapid, redisStore)

        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to service.event.name,
                Key.UUID.str to foresporselid,
                Key.INITIATE_ID.str to transactionId
            )
        )
        assertNull(redisStore.get(transactionId))
        testRapid.sendTestMessage(packet.toJson())

        assertNotNull(redisStore.get(transactionId))

        val im = "inntektsmelding_FTW"
        val svarMelding: JsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to service.event.name,
                Key.UUID.str to foresporselid,
                Key.INITIATE_ID.str to transactionId,
                Key.INNTEKTSMELDING_DOKUMENT.str to im
            )
        )
        testRapid.reset()
        testRapid.sendTestMessage(svarMelding.toJson())
        assertEquals(im, redisStore.get(transactionId))
    }
}
