package no.nav.helsearbeidsgiver.felles

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.utils.mapOfNotNull

data class Fail(
    @JsonIgnore
    val eventName: EventName?,
    val behov: BehovType?,
    val feilmelding: String,
    val data: Map<DataFelt, Any?>?,
    val uuid: String?,
    val forespørselId: String?
) {
    fun toJsonMessage(): JsonMessage {
        return JsonMessage.newMessage(
            mapOfNotNull(
                Key.EVENT_NAME.str to eventName?.name,
                Key.FAIL.str to this,
                Key.UUID.str to this.uuid
            )
        )
    }
}

fun JsonMessage.toFeilMessage(): Fail {
    return customObjectMapper().treeToValue(this[Key.FAIL.str], Fail::class.java).copy(eventName = EventName.valueOf(this[Key.EVENT_NAME.str].asText()))
}

fun JsonMessage.createFail(feilmelding: String, data: Map<DataFelt, Any?>? = null, behovType: BehovType? = null): Fail {
    val behovNode: JsonNode? = this.valueNullable(Key.BEHOV)
    // behovtype trenger å vare definert eksplisit da behov elemente er en List
    val behov: BehovType? = behovType ?: if (behovNode != null) BehovType.valueOf(behovNode.asText()) else null
    val forespørselId = this.valueNullableOrUndefined(Key.FORESPOERSEL_ID)?.asText()
    val eventName = this.valueNullableOrUndefined(Key.EVENT_NAME)?.asText()
    return Fail(eventName?.let { EventName.valueOf(eventName) }, behov, feilmelding, data, this.valueNullable(Key.UUID)?.asText(), forespørselId)
}
