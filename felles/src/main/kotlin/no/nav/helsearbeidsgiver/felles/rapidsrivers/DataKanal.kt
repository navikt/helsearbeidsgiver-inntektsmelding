package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key

abstract class DataKanal(val rapidsConnection: RapidsConnection) : River.PacketListener {
    abstract val eventName: EventName

    init {
        configure(
            River(rapidsConnection).apply {
                validate(accept())
            }
        ).register(this)
    }

    abstract fun accept(): River.PacketValidation

    private fun configure(river: River): River {
        return river.validate {
            it.demandValue(Key.EVENT_NAME.str, eventName.name)
            it.demandKey(Key.DATA.str)
            it.rejectKey(Key.BEHOV.str)
            it.rejectKey(Key.LØSNING.str)
            it.requireKey(Key.UUID.str)
            it.interestedIn(Key.FORESPOERSEL_ID.str)
            it.interestedIn(Key.CLIENT_ID.str)
        }
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        onData(packet)
    }

    abstract fun onData(packet: JsonMessage)
}
