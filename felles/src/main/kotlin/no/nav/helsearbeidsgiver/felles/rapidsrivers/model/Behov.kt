package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.json.JsonElement
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
import no.nav.helsearbeidsgiver.utils.json.parseJson
import java.util.UUID

class Behov(
    val event: EventName,
    val behov: BehovType,
    val forespoerselId: String?,
    val jsonMessage: JsonMessage
) : TxMessage {

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
            map: Map<IKey, Any> = emptyMap(),
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
    }

    fun createData(map: Map<DataFelt, Any>): Data {
        val forespoerselID = jsonMessage[Key.FORESPOERSEL_ID.str]
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

    fun createFail(feilmelding: String): Fail =
        Fail(
            feilmelding = feilmelding,
            event = event,
            transaksjonId = uuid().takeUnless { it.isBlank() }?.let(UUID::fromString),
            forespoerselId = forespoerselId?.takeUnless { it.isBlank() }?.let(UUID::fromString),
            utloesendeMelding = jsonMessage.toJson().parseJson()
        )

    fun createBehov(behov: BehovType, data: Map<IKey, Any>): Behov {
        return Behov(
            this.event,
            behov,
            forespoerselId,
            JsonMessage.newMessage(
                eventName = event.name,
                map = mapOfNotNull(
                    Key.BEHOV.str to behov.name,
                    Key.UUID.str to uuid().takeUnless { it.isBlank() },
                    Key.FORESPOERSEL_ID.str to this.forespoerselId
                ) + data.mapKeys { it.key.str }
            )
        )
    }

    fun createEvent(event: EventName, data: Map<IKey, Any>): Event {
        return Event.create(event, forespoerselId, data + mapOfNotNull(Key.TRANSACTION_ORIGIN to this.uuid().ifEmpty { null }))
    }

    fun toJson(): JsonElement =
        jsonMessage.toJson().parseJson()

    override fun uuid() = jsonMessage[Key.UUID.str].takeUnless { it.isMissingOrNull() }?.asText().orEmpty()

    operator fun get(key: IKey): JsonNode = jsonMessage[key.str]
}
