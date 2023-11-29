package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.utils.mapOfNotNull

class Behov(
    val event: EventName,
    val behov: BehovType,
    val forespoerselId: String?,
    val jsonMessage: JsonMessage
) {

    init {
        packetValidator.validate(jsonMessage)
        jsonMessage.demandValue(Key.EVENT_NAME.str, event.name)
        jsonMessage.demandValue(Key.BEHOV.str, behov.name)
    }
    companion object {
        val packetValidator = River.PacketValidation {
            it.demandKey(Key.EVENT_NAME.str)
            it.demandKey(Key.BEHOV.str)
            it.rejectKey(Key.DATA.str)
            it.rejectKey(Key.LØSNING.str)
            it.rejectKey(Key.FAIL.str)
            it.interestedIn(Key.UUID.str)
            it.interestedIn(Key.FORESPOERSEL_ID.str)
        }

        fun create(
            event: EventName,
            behov: BehovType,
            forespoerselId: String,
            map: Map<Key, Any> = emptyMap(),
            packetValidation: River.PacketValidation = River.PacketValidation { }
        ): Behov {
            return Behov(
                event = event,
                behov = behov,
                forespoerselId = forespoerselId,
                jsonMessage = JsonMessage.newMessage(
                    event.name,
                    mapOf(
                        Key.BEHOV.str to behov.name,
                        Key.FORESPOERSEL_ID.str to forespoerselId
                    ) + map.mapKeys { it.key.str }
                )
            ).also {
                packetValidation.validate(it.jsonMessage)
            }
        }

        fun create(jsonMessage: JsonMessage): Behov {
            return Behov(
                EventName.valueOf(jsonMessage[Key.EVENT_NAME.str].asText()),
                BehovType.valueOf(jsonMessage[Key.BEHOV.str].asText()),
                jsonMessage[Key.FORESPOERSEL_ID.str].asText(),
                jsonMessage
            )
        }
    }

    operator fun get(key: Key): JsonNode = jsonMessage[key.str]

    operator fun contains(key: Key): Boolean = jsonMessage[key.str].isMissingOrNull().not()

    fun createData(map: Map<Key, Any>): Data {
        val forespoerselID = this[Key.FORESPOERSEL_ID]
        return Data(
            event,
            JsonMessage.newMessage(
                event.name,
                mapOfNotNull(
                    Key.DATA.str to "",
                    Key.UUID.str to this.uuid().takeUnless { it.isBlank() },
                    Key.FORESPOERSEL_ID.str to forespoerselID
                ) + map.mapKeys { it.key.str }
            )
        )
    }

    fun createFail(feilmelding: String, data: Map<Key, Any> = emptyMap()): Fail {
        val forespoerselID = this[Key.FORESPOERSEL_ID]

        return Fail.create(
            event = event,
            behov = behov,
            feilmelding = feilmelding,
            uuid = this.uuid(),
            data = mapOfNotNull(
                Key.UUID to this.uuid().takeUnless { it.isBlank() },
                Key.FORESPOERSEL_ID to forespoerselID
            ) + data.mapKeys { it.key }
        )
    }

    fun createBehov(behov: BehovType, data: Map<Key, Any>): Behov {
        return Behov(
            this.event,
            behov,
            forespoerselId,
            JsonMessage.newMessage(
                eventName = event.name,
                map = mapOfNotNull(
                    Key.BEHOV.str to behov.name,
                    Key.UUID.str to this.uuid().takeUnless { it.isBlank() },
                    Key.FORESPOERSEL_ID.str to this.forespoerselId
                ) + data.mapKeys { it.key.str }
            )
        )
    }

    fun createEvent(event: EventName, data: Map<Key, Any>): Event {
        return Event.create(event, forespoerselId, data)
    }

    fun uuid() = jsonMessage[Key.UUID.str].takeUnless { it.isMissingOrNull() }?.asText().orEmpty()
}
