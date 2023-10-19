package no.nav.helsearbeidsgiver.felles.rapidsrivers

import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class DelegatingFailKanalTest {

    private val testRapid = TestRapid()
    private val mockPacketListener = mockk<River.PacketListener>(relaxed = true)

    init {
        DelegatingFailKanal(EventName.INSENDING_STARTED, mockPacketListener, testRapid)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearAllMocks()
    }

    @Test
    fun `FAIL bør vare delegert`() {
        testRapid.sendJson(
            Key.FAIL to mockFail(EventName.INSENDING_STARTED).toJson(Fail.serializer())
        )
        verify(exactly = 1) { mockPacketListener.onPacket(any(), any()) }
    }

    @Test
    fun `fanger ikke FAIL med en annen event`() {
        testRapid.sendJson(
            Key.FAIL to mockFail(EventName.KVITTERING_REQUESTED).toJson(Fail.serializer())
        )
        verify(exactly = 0) { mockPacketListener.onPacket(any(), any()) }
    }
}

private fun mockFail(event: EventName): Fail =
    Fail(
        feilmelding = "failando, failando",
        event = event,
        transaksjonId = UUID.randomUUID(),
        forespoerselId = UUID.randomUUID(),
        utloesendeMelding = JsonNull
    )
