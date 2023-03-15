package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser

class GenericFailListener(val mainListener: River.PacketListener, rapidsConnection: RapidsConnection) : Løser(rapidsConnection) {

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.EVENT_NAME.str, EventName.INSENDING_STARTED.name)
            it.demandKey(Key.FAIL.str)
            it.interestedIn(Key.UUID.str)
        }
    }

    override fun onBehov(packet: JsonMessage) {
        mainListener.onPacket(packet, rapidsConnection)
    }
}
