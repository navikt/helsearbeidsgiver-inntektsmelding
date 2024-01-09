package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import io.mockk.clearAllMocks
import io.mockk.verify
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class KvitteringServiceTest {
    private val testRapid: TestRapid = TestRapid()
    private val mockRedis = MockRedis()

    private val foresporselId = UUID.randomUUID()

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
        val transaksjonId = UUID.randomUUID()
        val im = "inntektsmelding_FTW"

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.FORESPOERSEL_ID to foresporselId.toJson()
        )

        testRapid.reset()

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to foresporselId.toJson(),
            Key.DATA to "".toJson(),
            Key.INNTEKTSMELDING_DOKUMENT to im.toJson()
        )

        verify {
            mockRedis.store.set(RedisKey.of(transaksjonId, Key.INNTEKTSMELDING_DOKUMENT), im)
        }
    }
}
