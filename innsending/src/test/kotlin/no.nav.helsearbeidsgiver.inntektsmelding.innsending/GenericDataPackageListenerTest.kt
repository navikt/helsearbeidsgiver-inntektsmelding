package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class GenericDataPackageListenerTest {

    enum class NOTFOUND {
        FELT
    }

    private var mockListener: River.PacketListener = mockk()

    private lateinit var dataListener: StatefullDataKanal

    private val redisStore = mockk<RedisStore>()

    private val testRapid: TestRapid = TestRapid()

    @BeforeEach
    fun beforeAll() {
        dataListener = StatefullDataKanal(
            arrayOf(DataFelt.FNR),
            EventName.INSENDING_STARTED,
            mockListener,
            testRapid,
            redisStore
        )
    }

    @Test
    fun `Listener fanger data`() {
        every { mockListener.onPacket(any(), any()) } answers {
            val jsonMessages: JsonMessage = it.invocation.args[0] as JsonMessage
            assert(jsonMessages[DataFelt.FNR.str].asText() == "Hello")
        }
        every { redisStore.set(any<RedisKey>(), any<String>(), 60L) } returns Unit
        val uuid: UUID = UUID.randomUUID()
        testRapid.sendTestMessage(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INSENDING_STARTED.name,
                    Key.DATA.str to "",
                    Key.UUID.str to uuid.toString(),
                    DataFelt.FNR.str to "Hello",
                    "enAnnenFelt" to "Not captured"
                )
            ).toJson()
        )
        verify(exactly = 1) {
            redisStore.set(RedisKey.of(uuid, DataFelt.FNR), "Hello", 60L)
            mockListener.onPacket(any(), any())
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
            redisStore.set(any<String>(), any())
            mockListener.onPacket(any(), any())
        }
    }
}
