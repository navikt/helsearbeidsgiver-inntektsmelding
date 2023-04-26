package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName

abstract class DelegatingEventListener(val mainListener: River.PacketListener, rapidsConnection: RapidsConnection) : EventListener(rapidsConnection){

    override fun onEvent(packet: JsonMessage) {
        mainListener.onPacket(packet,rapidsConnection)
    }
}
