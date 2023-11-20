package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.TxMessage
import java.util.UUID

class Data(val event: EventName, val jsonMessage: JsonMessage) : TxMessage {

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

        fun create(event: EventName, uuid: UUID, map: Map<DataFelt, Any> = emptyMap()): Data {
            return Data(event, JsonMessage.newMessage(event.name, mapOf(Key.DATA.str to "", Key.UUID.str to uuid.toString()) + map.mapKeys { it.key.str }))
        }

        fun create(jsonMessage: JsonMessage): Data {
            return Data(EventName.valueOf(jsonMessage[Key.EVENT_NAME.str].asText()), jsonMessage)
        }
    }

    override fun uuid(): String = jsonMessage[Key.UUID.str].takeUnless { it.isMissingOrNull() }?.asText().orEmpty()
}
