package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.TxMessage
import no.nav.helsearbeidsgiver.felles.utils.mapOfNotNull
import java.util.UUID

class Event(
    val event: EventName,
    val forespoerselId: String? = null,
    val jsonMessage: JsonMessage,
    val clientId: String? = null
) : TxMessage {

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
            it.interestedIn(Key.CLIENT_ID.str)
            it.interestedIn(Key.FORESPOERSEL_ID.str)
        }

        fun create(event: EventName, forespoerselId: String?, map: Map<IKey, Any> = emptyMap()): Event {
            return Event(
                event,
                forespoerselId,
                JsonMessage.newMessage(event.name, mapOfNotNull(Key.FORESPOERSEL_ID.str to forespoerselId) + map.mapKeys { it.key.str })
            )
        }
        fun create(jsonMessage: JsonMessage): Event {
            val event = EventName.valueOf(jsonMessage[Key.EVENT_NAME.str].asText())
            val clientID = jsonMessage[Key.CLIENT_ID.str].takeUnless { it.isMissingOrNull() }?.asText()
            val forespoerselId = jsonMessage[Key.FORESPOERSEL_ID.str].takeUnless { it.isMissingOrNull() }?.asText()
            return Event(event, forespoerselId, jsonMessage, clientID)
        }
    }

    fun createFail(feilmelding: String, data: Map<IKey, Any> = emptyMap()): Fail {
        val forespoerselID = jsonMessage[Key.FORESPOERSEL_ID.str]
        return Fail.create(
            event,
            null,
            feilmelding,
            data = mapOfNotNull(
                Key.UUID to this.uuid().takeUnless { it.isBlank() },
                Key.FORESPOERSEL_ID to forespoerselID
            ) + data.mapKeys { it.key }
        )
    }

    override fun uuid() = this.uuid.orEmpty()
}
