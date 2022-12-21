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

fun JsonMessage.requireKeys(vararg keys: Key) {
    val keysAsStr = keys.map(Key::str).toTypedArray()
    this.requireKey(*keysAsStr)
}

fun JsonNode.asUuid(): UUID =
    asText().let(UUID::fromString)

fun MessageContext.publish(vararg keyValuePairs: Pair<Key, Any>) {
    keyValuePairs.toMap()
        .mapKeys { (key, _) -> key.str }
        .let(JsonMessage::newMessage)
        .toJson()
        .let(this::publish)
}
