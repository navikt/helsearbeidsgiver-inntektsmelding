package no.nav.helsearbeidsgiver.felles.rapidsrivers

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.json.toJsonNode
import no.nav.helsearbeidsgiver.utils.pipe.mapFirst

fun JsonMessage.demandAll(key: Key, values: List<BehovType>) {
    demandAll(key.str, values.map(BehovType::name))
}

fun JsonMessage.demandValue(pair: Pair<Key, BehovType>) {
    demandValue(pair.first.str, pair.second.name)
}

fun JsonMessage.demandKeys(vararg keys: Key) {
    keys.map(Key::str)
        .onEach(this::demandKey)
}

fun JsonMessage.demand(vararg keyAndParserPairs: Pair<Key, (JsonElement) -> Any>) {
    val keyStringAndParserPairs = keyAndParserPairs.map { it.mapFirst(Key::str) }
    validate(JsonMessage::demand, keyStringAndParserPairs)
}

fun JsonMessage.rejectKeys(vararg keys: Key) {
    val keysAsStr = keys.map(Key::str).toTypedArray()
    rejectKey(*keysAsStr)
}

fun JsonMessage.requireKeys(vararg keys: Key) {
    val keysAsStr = keys.map(Key::str).toTypedArray()
    requireKey(*keysAsStr)
}

fun JsonMessage.require(vararg keyAndParserPairs: Pair<Key, (JsonElement) -> Any>) {
    val keyStringAndParserPairs = keyAndParserPairs.map { it.mapFirst(Key::str) }
    validate(JsonMessage::require, keyStringAndParserPairs)
}

fun JsonMessage.interestedIn(vararg keyAndParserPairs: Pair<Key, (JsonElement) -> Any>) {
    val keyStringAndParserPairs = keyAndParserPairs.map { it.mapFirst(Key::str) }
    validate(JsonMessage::interestedIn, keyStringAndParserPairs)
}

fun JsonMessage.interestedIn(vararg keys: Key) {
    val keysAsStr = keys.map(Key::str).toTypedArray()
    interestedIn(*keysAsStr)
}

fun MessageContext.publish(
    vararg messageFields: Pair<IKey, JsonElement>,
    block: ((JsonMessage) -> Unit)? = null
): String =
    messageFields
        .associate { (key, value) -> key.toString() to value.toJsonNode() }
        .let(JsonMessage::newMessage)
        .also {
            publish(it.id, it.toJson())
            if (block != null) block(it)
        }
        .id

private fun JsonMessage.validate(
    validateFn: (JsonMessage, String, (JsonNode) -> Any) -> Unit,
    keyAndParserPairs: List<Pair<String, (JsonElement) -> Any>>
) {
    keyAndParserPairs.forEach { (key, block) ->
        validateFn(this, key) {
            it.toJsonElement().let(block)
        }
    }
}
