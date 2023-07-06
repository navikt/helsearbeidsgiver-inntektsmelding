package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key

abstract class StatelessLøser(val rapidsConnection: RapidsConnection) : River.PacketListener {

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
            it.demandKey(Key.FORESPOERSEL_ID.str)
            it.rejectKey(Key.LØSNING.str)
            it.interestedIn(Key.UUID.str)
        }
    }

    fun publishBehov(message: JsonMessage) {
        rapidsConnection.publish(message.toJson())
    }

    // TODO: om denne skal benyttes og fungere, må den i så fall skrelle vekk Behov før den sender..
    fun publishEvent(message: JsonMessage) {
        throw NotImplementedError()
        // rapidsConnection.publish(message.toJson())
    }

    fun publishData(message: JsonMessage) {
        rapidsConnection.publish(message.toJson())
    }

    fun publishFail(fail: Fail) {
        rapidsConnection.publish(fail.toJsonMessage().toJson())
    }

    fun publishFail(message: JsonMessage) {
        rapidsConnection.publish(message.toJson())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        onBehov(packet)
    }

    abstract fun onBehov(packet: JsonMessage)
}
