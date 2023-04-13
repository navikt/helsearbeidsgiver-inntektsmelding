package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key

abstract class Løser(val rapidsConnection: RapidsConnection) : River.PacketListener {
    lateinit var eventName: EventName

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
            it.demandKey(Key.EVENT_NAME.str)
            it.demandKey(Key.BEHOV.str)
            it.rejectKey(Key.LØSNING.str)
            it.interestedIn(Key.UUID.str)
        }
    }

    fun publishBehov(message: JsonMessage) {
        message.set(Key.EVENT_NAME.str, eventName.name)
        rapidsConnection.publish(message.toJson())
    }

    fun publishEvent(message: JsonMessage) {
        rapidsConnection.publish(message.toJson())
    }

    fun publishData(message: JsonMessage) {
        message.set(Key.EVENT_NAME.str, eventName.name)
        rapidsConnection.publish(message.toJson())
    }

    fun publishFail(message: JsonMessage) {
        message.set(Key.EVENT_NAME.str, eventName.name)
        rapidsConnection.publish(message.toJson())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        eventName = EventName.valueOf(packet.get(Key.EVENT_NAME.str).asText())
        onBehov(packet)
    }

    abstract fun onBehov(packet: JsonMessage)
}
