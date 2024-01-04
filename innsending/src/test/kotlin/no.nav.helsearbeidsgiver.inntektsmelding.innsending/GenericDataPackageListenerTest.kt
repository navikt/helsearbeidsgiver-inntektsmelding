package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class GenericDataPackageListenerTest {

    enum class NOTFOUND {
        FELT
    }

    private var mockListener = mockk<River.PacketListener>(relaxed = true)

    private val mockRedis = MockRedis()

    private val testRapid: TestRapid = TestRapid()

    init {
        StatefullDataKanal(
            testRapid,
            EventName.INSENDING_STARTED,
            mockRedis.store,
            listOf(Key.FNR, Key.INNTEKT),
            mockListener::onPacket
        )
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearAllMocks()
        mockRedis.setup()
    }

    @Test
    fun `Listener fanger data`() {
        val uuid: UUID = UUID.randomUUID()

        testRapid.sendTestMessage(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INSENDING_STARTED.name,
                    Key.DATA.str to "",
                    Key.UUID.str to uuid.toString(),
                    Key.FNR.str to "Hello",
                    "enAnnenFelt" to "Not captured"
                )
            ).toJson()
        )

        verify(exactly = 1) {
            mockRedis.store.set(RedisKey.of(uuid, Key.FNR), "Hello")
            mockListener.onPacket(
                withArg {
                    it[Key.FNR.str].asText() shouldBe "Hello"
                },
                any()
            )
        }
    }

    @Test
    fun `Data ikke fanget`() {
        testRapid.sendTestMessage(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INSENDING_STARTED.name,
                    Key.DATA.str to "",
                    Key.UUID.str to "uuid",
                    NOTFOUND.FELT.name to "Hello",
                    "enAnnenFelt" to "Not captured"
                )
            ).toJson()
        )
        verify(exactly = 0) {
            mockRedis.store.set(any(), any())
            mockListener.onPacket(any(), any())
        }
    }
}
