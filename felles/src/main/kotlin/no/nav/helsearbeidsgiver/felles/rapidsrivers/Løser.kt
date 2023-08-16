package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Data

abstract class Løser(val rapidsConnection: RapidsConnection) : River.PacketListener {
    lateinit var eventName: EventName
    lateinit var forespoerselId: String

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
            it.interestedIn(Key.FORESPOERSEL_ID.str)
        }
    }

    // Ungå å bruke det, hvis du kan.
    // Alle løser som publiserer Behov vil få kunskap om nedstrøms løserne.
    // i tilleg gjenbruktbarhet av løseren vil vare betydelig redusert
    fun publishBehov(message: JsonMessage) {
        message[Key.EVENT_NAME.str] = eventName.name
        if (forespoerselId.isNotEmpty()) {
            message[Key.FORESPOERSEL_ID.str] = forespoerselId
        }
        rapidsConnection.publish(message.toJson())
    }

    fun publishEvent(message: JsonMessage) {
        if (forespoerselId.isNotEmpty()) {
            message[Key.FORESPOERSEL_ID.str] = forespoerselId
        }
        rapidsConnection.publish(message.toJson())
    }

    fun publishData(data: Data) {
        rapidsConnection.publish(data.toJsonMessage().toJson())
    }

    fun publishData(message: JsonMessage) {
        message[Key.EVENT_NAME.str] = eventName.name
        rapidsConnection.publish(message.toJson())
    }

    fun publishFail(fail: Fail) {
        rapidsConnection.publish(fail.toJsonMessage().toJson())
    }

    fun publishFail(message: JsonMessage) {
        message[Key.EVENT_NAME.str] = eventName.name
        rapidsConnection.publish(message.toJson())
    }
    fun publishFail(fail: no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail) {
        rapidsConnection.publish(fail.toJsonMessage().toJson())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        eventName = EventName.valueOf(packet[Key.EVENT_NAME.str].asText())
        forespoerselId = packet[Key.FORESPOERSEL_ID.str].asText()
        onBehov(packet)
        if (!packet[Key.BEHOV.str].isArray) {
            val behov = Behov.create(packet)
            onBehov(behov)
        }
    }

    abstract fun onBehov(packet: JsonMessage)

    open fun onBehov(behov: Behov) {
    }
}
