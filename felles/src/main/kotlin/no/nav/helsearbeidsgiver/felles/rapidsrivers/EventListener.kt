package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key

abstract class EventListener(val rapidsConnection: RapidsConnection) : River.PacketListener {

    abstract val event: EventName
    lateinit var ex: (packet: JsonMessage, context: MessageContext) -> Unit

    init {
        configureAsListener(
            River(rapidsConnection).apply {
                validate(accept())
            }
        ).register(this)
    }

    abstract fun accept(): River.PacketValidation

    private fun configureAsListener(river: River): River {
        return river.validate {
            it.demandValue(Key.EVENT_NAME.str, event.name)
            it.rejectKey(Key.BEHOV.str)
            it.rejectKey(Key.LØSNING.str)
            it.rejectKey(Key.DATA.str)
            it.interestedIn(Key.UUID.str)
        }
    }

    fun publishBehov(message: JsonMessage) {
        message.set(Key.EVENT_NAME.str, event.name)
        rapidsConnection.publish(message.toJson())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        onEvent(packet)
    }

    abstract fun onEvent(packet: JsonMessage)
}
