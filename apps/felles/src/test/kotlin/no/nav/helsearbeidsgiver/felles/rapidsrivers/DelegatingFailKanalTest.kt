package no.nav.helsearbeidsgiver.felles.rapidsrivers

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import org.junit.jupiter.api.Test
import java.util.UUID

class DelegatingFailKanalTest {

    private val testRapid = TestRapid()
    private val mockPacketListener: River.PacketListener = mockk()
    val failKanal = DelegatingFailKanal(EventName.INSENDING_STARTED, mockPacketListener, testRapid)

    @Test
    fun `FAIL bør vare delegert`() {
        every { mockPacketListener.onPacket(any(), any()) } returns Unit
        testRapid.sendTestMessage(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INSENDING_STARTED.name,
                    Key.UUID.str to UUID.randomUUID().toString(),
                    Key.FAIL.str to "This is a fail"
                )
            ).toJson()
        )
        verify(exactly = 1) { mockPacketListener.onPacket(any(), any()) }
    }

    @Test
    fun `fanger ikke FAIL med en annen event`() {
        every { mockPacketListener.onPacket(any(), any()) } returns Unit
        testRapid.sendTestMessage(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.KVITTERING_REQUESTED,
                    Key.UUID.str to UUID.randomUUID().toString(),
                    Key.FAIL.str to "This is a fail"
                )
            ).toJson()
        )
        verify(exactly = 0) { mockPacketListener.onPacket(any(), any()) }
    }
}
