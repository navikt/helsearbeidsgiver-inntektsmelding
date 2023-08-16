package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.TxMessage
import no.nav.helsearbeidsgiver.felles.utils.mapOfNotNull
import java.lang.IllegalArgumentException

class Event(val event: EventName, val forespoerselId: String? = null, private val jsonMessage: JsonMessage, val clientId: String? = null) : Message, TxMessage {

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

        fun create(event: EventName, forespoerselId: String, map: Map<IKey, Any> = emptyMap()): Event {
            return Event(event, forespoerselId, JsonMessage.newMessage(event.name, map.mapKeys { it.key.str }))
        }
        fun create(jsonMessage: JsonMessage): Event {
            val event = EventName.valueOf(jsonMessage[Key.EVENT_NAME.str].asText())
            val clientID = jsonMessage[Key.CLIENT_ID.str].takeUnless { it.isMissingOrNull() }?.asText()
            val forespoerselId = jsonMessage[Key.FORESPOERSEL_ID.str].takeUnless { it.isMissingOrNull() }?.asText()
            return Event(event, forespoerselId, jsonMessage, clientID)
        }
    }

    override operator fun get(key: IKey): JsonNode = jsonMessage[key.str]

    override operator fun set(key: IKey, value: Any) {
        if (key == Key.EVENT_NAME || key == Key.BEHOV || key == Key.CLIENT_ID) throw IllegalArgumentException("Set ${key.str} er ikke tillat. ")
        jsonMessage[key.str] = value
    }

    fun createBehov(behov: BehovType, map: Map<DataFelt, Any>): Behov {
        val forespoerselID = jsonMessage[Key.FORESPOERSEL_ID.str].takeUnless { it.isMissingOrNull() }
        return Behov(
            event,
            behov,
            this.forespoerselId,
            JsonMessage.newMessage(
                event.name,
                mapOfNotNull(
                    Key.BEHOV.str to behov.name,
                    Key.UUID.str to this.uuid,
                    Key.FORESPOERSEL_ID.str to forespoerselID
                ) + map.mapKeys { it.key.str }
            )
        )
    }

    override fun uuid() = this.uuid.orEmpty()

    override fun toJsonMessage(): JsonMessage {
        return this.jsonMessage
    }
}
