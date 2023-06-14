package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key

abstract class Løser(val rapidsConnection: RapidsConnection) : River.PacketListener {

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

    // Ungå å bruke det, hvis du kan.
    // Alle løser som publiserer Behov vil få kunskap om nedstrøms løserne.
    // i tilleg gjenbruktbarhet av løseren vil vare betydelig redusert
    fun publishBehov(message: JsonMessage) {
        rapidsConnection.publish(message.toJson())
    }

    fun publishEvent(message: JsonMessage) {
        rapidsConnection.publish(message.toJson())
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

    fun getEvent(packet: JsonMessage): EventName {
        return EventName.valueOf(packet[Key.EVENT_NAME.str].asText())
    }

    fun getForesporselId(packet: JsonMessage): String {
        return packet[Key.FORESPOERSEL_ID.str].asText()
    }
}
