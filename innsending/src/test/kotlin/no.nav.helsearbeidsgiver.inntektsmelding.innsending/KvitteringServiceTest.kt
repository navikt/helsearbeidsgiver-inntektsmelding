package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import io.mockk.clearAllMocks
import io.mockk.verify
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KvitteringServiceTest {
    private val testRapid: TestRapid = TestRapid()
    private val mockRedis = MockRedis()

    private val foresporselid = "abc"

    init {
        KvitteringService(testRapid, mockRedis.store)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearAllMocks()
        mockRedis.setup()
    }

    @Test
    fun kvitteringServiceTest() {
        val uuid = randomUuid()
        val im = "inntektsmelding_FTW"

        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.KVITTERING_REQUESTED.name,
                Key.FORESPOERSEL_ID.str to foresporselid
            )
        )

        testRapid.sendTestMessage(packet.toJson())

        val behov = Behov(
            EventName.KVITTERING_REQUESTED,
            BehovType.HENT_PERSISTERT_IM,
            foresporselid,
            JsonMessage.newMessage(
                mapOf(
                    Key.BEHOV.str to BehovType.HENT_PERSISTERT_IM,
                    Key.EVENT_NAME.str to EventName.KVITTERING_REQUESTED.name,
                    Key.FORESPOERSEL_ID.str to foresporselid,
                    Key.UUID.str to uuid.toString()
                )
            )
        )

        testRapid.reset()
        testRapid.sendTestMessage(behov.createData(mapOf(DataFelt.INNTEKTSMELDING_DOKUMENT to im)).jsonMessage.toJson())

        verify {
            mockRedis.store.set(RedisKey.of(uuid, DataFelt.INNTEKTSMELDING_DOKUMENT), im)
        }
    }
}
