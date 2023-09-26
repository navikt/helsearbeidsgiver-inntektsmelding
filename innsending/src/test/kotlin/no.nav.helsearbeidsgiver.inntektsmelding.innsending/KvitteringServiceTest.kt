package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedisStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KvitteringServiceTest {
    private val redisStore = MockRedisStore()

    private val testRapid: TestRapid = TestRapid()

    private val foresporselid = "abc"

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun kvitteringServiceTest() {
        val service = KvitteringService(testRapid, redisStore)

        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to service.event.name,
                Key.FORESPOERSEL_ID.str to foresporselid
            )
        )

        testRapid.sendTestMessage(packet.toJson())
        val uuid = redisStore.get("uuid") // finn den genererte uuid'en fra service
        println("Fant uuid: $uuid")

        val im = "inntektsmelding_FTW"
        val behov = Behov(
            service.event,
            BehovType.HENT_PERSISTERT_IM,
            foresporselid,
            JsonMessage.newMessage(
                mapOf(
                    Key.BEHOV.str to BehovType.HENT_PERSISTERT_IM,
                    Key.EVENT_NAME.str to service.event.name,
                    Key.FORESPOERSEL_ID.str to foresporselid,
                    Key.UUID.str to uuid.toString()
                )
            )
        )
        testRapid.reset()
        testRapid.sendTestMessage(behov.createData(mapOf(DataFelt.INNTEKTSMELDING_DOKUMENT to im)).toJsonMessage().toJson())
        assertEquals(im, redisStore.get(uuid + DataFelt.INNTEKTSMELDING_DOKUMENT))
    }
}
