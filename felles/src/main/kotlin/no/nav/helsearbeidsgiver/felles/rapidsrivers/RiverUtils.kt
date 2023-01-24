package no.nav.helsearbeidsgiver.felles.rapidsrivers

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import java.util.UUID

fun JsonMessage.demandAll(key: Key, values: List<BehovType>) {
    demandAll(key.str, values.map(BehovType::name))
}

fun JsonMessage.rejectKeys(vararg keys: Key) {
    val keysAsStr = keys.map(Key::str).toTypedArray()
    rejectKey(*keysAsStr)
}

fun JsonMessage.requireKeys(vararg keys: Key) {
    val keysAsStr = keys.map(Key::str).toTypedArray()
    requireKey(*keysAsStr)
}

fun JsonMessage.requireTypes(vararg keys: Pair<Key, (JsonNode) -> Any>) {
    keys.forEach { (key, block) ->
        this.require(key.str, block)
    }
}

fun JsonNode.asUuid(): UUID =
    asText().let(UUID::fromString)

fun MessageContext.publish(
    vararg messageFields: Pair<Key, Any>,
    block: ((JsonMessage) -> Unit)? = null
): String =
    messageFields.toMap()
        .mapKeys { (key, _) -> key.str }
        .let(JsonMessage::newMessage)
        .also {
            publish(it.id, it.toJson())
            if (block != null) block(it)
        }
        .id
