package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal

class GenericFailListener(
    override val eventName: EventName,
    val mainListener: River.PacketListener,
    rapidsConnection: RapidsConnection
) : FailKanal(rapidsConnection) {
    override fun onFail(packet: JsonMessage) {
        mainListener.onPacket(packet, rapidsConnection)
    }
}
