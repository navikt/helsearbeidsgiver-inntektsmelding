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

class Behov(
    private val event: EventName,
    private val behov: BehovType,
    private val jsonMessage: JsonMessage
) : Message, TxMessage {

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
            it.rejectKey(Key.FAIL.str)
            it.interestedIn(Key.UUID.str)
        }

        fun create(event: EventName, behov: BehovType, map: Map<IKey, Any> = emptyMap()): Behov {
            return Behov(event, behov, JsonMessage.newMessage(event.name, mapOf(Key.BEHOV.str to behov.name) + map.mapKeys { it.key.str }))
        }

        fun create(jsonMessage: JsonMessage): Behov {
            return Behov(EventName.valueOf(jsonMessage[Key.EVENT_NAME.str].asText()), BehovType.valueOf(jsonMessage[Key.BEHOV.str].asText()), jsonMessage)
        }
    }

    override operator fun get(key: IKey): JsonNode = jsonMessage[key.str]

    override operator fun set(key: IKey, value: Any) {
        if (key == Key.EVENT_NAME || key == Key.BEHOV || key == Key.CLIENT_ID) throw IllegalArgumentException("Set ${key.str} er ikke tillat. ")
        jsonMessage[key.str] = value
    }

    fun createData(map: Map<DataFelt, Any>): Data {
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

    fun createFail(feilmelding: String, data: Map<IKey, Any> = emptyMap()): Fail {
        val forespoerselID = this[Key.FORESPOERSEL_ID]

        return Fail.create(
            event, behov, feilmelding,
            mapOfNotNull(
                Key.UUID to this.uuid().takeUnless { it.isBlank() },
                Key.FORESPOERSEL_ID to forespoerselID
            ) + data.mapKeys { it.key }
        )
    }

    fun createBehov(behov: BehovType, data: Map<IKey, Any>): Behov {
        val forespoerselID = this[Key.FORESPOERSEL_ID]
        return Behov(
            this.event, behov,
            JsonMessage.newMessage(
                event.name,
                mapOfNotNull(
                    Key.BEHOV.str to behov.name,
                    Key.UUID.str to this.uuid().takeUnless { it.isBlank() },
                    Key.FORESPOERSEL_ID.str to forespoerselID
                ) + data.mapKeys { it.key.str }
            )
        )
    }

    override fun uuid() = jsonMessage[Key.UUID.str].takeUnless { it.isMissingOrNull() }?.asText().orEmpty()

    override fun toJsonMessage(): JsonMessage {
        return jsonMessage
    }
}
