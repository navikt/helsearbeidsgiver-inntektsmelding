package no.nav.helsearbeidsgiver.felles

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.felles.utils.mapOfNotNull

@Deprecated("Replace with rapidrivers.model.Fail")
@Serializable
data class Fail(
    @JsonIgnore
    val eventName: EventName? = null,
    val behov: BehovType? = null,
    val feilmelding: String,
    @JsonIgnore
    val data: Map<DataFelt, JsonElement?>? = null,
    val uuid: String?,
    val forespørselId: String?
) {
    fun toJsonMessage(): JsonMessage {
        val msg = JsonMessage.newMessage(
            mapOfNotNull(
                Key.EVENT_NAME.str to eventName?.name,
                Key.FAIL.str to this,
                Key.UUID.str to this.uuid
            )
        )
        msg.interestedIn(Key.EVENT_NAME.str, Key.FAIL.str, Key.UUID.str)
        return msg
    }
}

fun JsonMessage.toFeilMessage(): Fail {
    this.interestedIn(Key.FORESPOERSEL_ID.str, Key.EVENT_NAME.str, Key.FAIL.str, Key.UUID.str)
    return Fail(
        eventName = EventName.valueOf(this[Key.EVENT_NAME.str].asText()),
        feilmelding = this[Key.FAIL.str].asText(),
        uuid = this[Key.UUID.str].asText(),
        forespørselId = this[Key.FORESPOERSEL_ID.str].asText()
    )
}

fun JsonMessage.createFail(feilmelding: String, data: Map<DataFelt, JsonElement?>? = null, behovType: BehovType? = null): Fail {
    val behovNode: JsonNode? = this.valueNullable(Key.BEHOV)
    // behovtype trenger å vare definert eksplisit da behov elemente er en List
    val behov: BehovType? = behovType ?: if (behovNode != null) BehovType.valueOf(behovNode.asText()) else null
    val forespørselId = this.valueNullableOrUndefined(Key.FORESPOERSEL_ID)?.asText()
    val eventName = this.valueNullableOrUndefined(Key.EVENT_NAME)?.asText()
    return Fail(eventName?.let { EventName.valueOf(eventName) }, behov, feilmelding, data, this.valueNullable(Key.UUID)?.asText(), forespørselId)
}
