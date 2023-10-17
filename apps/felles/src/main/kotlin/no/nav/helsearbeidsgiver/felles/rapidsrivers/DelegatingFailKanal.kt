package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName

class DelegatingFailKanal(
    override val eventName: EventName,
    private val mainListener: River.PacketListener,
    rapidsConnection: RapidsConnection
) : FailKanal(rapidsConnection) {
    override fun onFail(packet: JsonMessage) {
        mainListener.onPacket(packet, rapidsConnection)
    }
}
