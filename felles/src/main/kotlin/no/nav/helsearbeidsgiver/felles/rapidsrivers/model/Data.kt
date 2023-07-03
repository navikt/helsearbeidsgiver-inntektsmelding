package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.TxMessage

class Data(val event: EventName, private val jsonMessage: JsonMessage) : Message, TxMessage {

    init {
        packetValidator.validate(jsonMessage)
        jsonMessage.demandValue(Key.EVENT_NAME.str, event.name)
    }
    companion object {
        val packetValidator = River.PacketValidation {
            it.demandKey(Key.EVENT_NAME.str)
            it.rejectKey(Key.BEHOV.str)
            it.demandKey(Key.DATA.str)
            it.rejectKey(Key.FAIL.str)
            it.interestedIn(Key.UUID.str)
        }

        fun create(event: EventName, map: Map<DataFelt, Any> = emptyMap()): Data {
            return Data(event, JsonMessage.newMessage(event.name, mapOf(Key.DATA.str to "") + map.mapKeys { it.key.str }))
        }

        fun create(jsonMessage: JsonMessage): Data {
            return Data(EventName.valueOf(jsonMessage[Key.EVENT_NAME.str].asText()), jsonMessage)
        }
    }

    override operator fun get(key: IKey): JsonNode = jsonMessage[key.str]

    override operator fun set(key: IKey, value: Any) { jsonMessage[key.str] = value }

    override fun uuid() = jsonMessage[Key.UUID.str].asText()

    override fun toJsonMessage(): JsonMessage {
        return jsonMessage
    }
}
