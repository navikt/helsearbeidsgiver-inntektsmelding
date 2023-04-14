package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key

class SimpleLosningKanal(rapidsConnection: RapidsConnection, eventName: EventName) : DataKanal(rapidsConnection) {

    override val eventName: EventName = EventName.KVITTERING_REQUESTED

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.EVENT_NAME.str, eventName.name)
        }
    }

    override fun onData(packet: JsonMessage) {
        TODO("Not yet implemented")
    }
}
