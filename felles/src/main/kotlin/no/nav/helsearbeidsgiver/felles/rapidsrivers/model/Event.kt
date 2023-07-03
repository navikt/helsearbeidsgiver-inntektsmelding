package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.TxMessage

class Event(val event: EventName, private val jsonMessage: JsonMessage, val clientId: String? = null) : Message, TxMessage {

    @Transient var uuid: String? = null

    init {
        packetValidator.validate(jsonMessage)
        jsonMessage.demandValue(Key.EVENT_NAME.str, event.name)
    }
    companion object {
        val packetValidator = River.PacketValidation {
            it.demandKey(Key.EVENT_NAME.str)
            it.rejectKey(Key.BEHOV.str)
            it.rejectKey(Key.DATA.str)
            it.rejectKey(Key.FAIL.str)
            it.rejectKey(Key.LØSNING.str)
            // midlertidig, generelt det bør vare reject
            it.interestedIn(Key.UUID.str)
            it.interestedIn(Key.TRANSACTION_ORIGIN.str)
            it.interestedIn(Key.CLIENT_ID.str)
            it.interestedIn(Key.FORESPOERSEL_ID.str)
        }

        fun create(event: EventName, map: Map<IKey, Any> = emptyMap()): Event {
            return Event(event, JsonMessage.newMessage(event.name, map.mapKeys { it.key.str }))
        }
        fun create(jsonMessage: JsonMessage): Event {
            val event = EventName.valueOf(jsonMessage[Key.EVENT_NAME.name].asText())
            val clientID = jsonMessage[Key.CLIENT_ID.str]?.asText()
            return Event(event, jsonMessage, clientID)
        }
    }

    override operator fun get(key: IKey): JsonNode = jsonMessage[key.str]

    override operator fun set(key: IKey, value: Any) { jsonMessage[key.str] = value }

/*
    fun createBehov(behov: BehovType,map: Map<DataFelt, Any>): Behov {
        return Behov(event, behov, JsonMessage.newMessage(event.value,mapOf(Key.BEHOV.str() to behov.value) + map.mapKeys { it.key.str }))
    }
*/
    override fun uuid() = this.uuid ?: ""

    override fun toJsonMessage(): JsonMessage {
        return jsonMessage
    }
}
