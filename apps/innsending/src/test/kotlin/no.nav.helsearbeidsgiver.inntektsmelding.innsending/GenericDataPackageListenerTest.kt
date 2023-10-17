package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class GenericDataPackageListenerTest {

    class MockListener(private val validation: (message: JsonMessage) -> Unit) : River.PacketListener {
        override fun onPacket(packet: JsonMessage, context: MessageContext) {
            validation.invoke(packet)
        }
    }

    enum class TestFelter {
        FELT1, FELT2
    }

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
            TestFelter.entries.map { it.toString() }.toTypedArray(),
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
            assert(jsonMessages[TestFelter.FELT1.name].asText() == "Hello")
        }
        every { redisStore.set(any<String>(), any(), 60L) } returns Unit
        val uuid: UUID = UUID.randomUUID()
        testRapid.sendTestMessage(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INSENDING_STARTED.name,
                    Key.DATA.str to "",
                    Key.UUID.str to uuid.toString(),
                    TestFelter.FELT1.name to "Hello",
                    "enAnnenFelt" to "Not captured"
                )
            ).toJson()
        )
        verify(exactly = 1) {
            redisStore.set(uuid.toString() + TestFelter.FELT1, "Hello", 60L)
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
