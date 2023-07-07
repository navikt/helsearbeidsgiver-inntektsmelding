package no.nav.helsearbeidsgiver.felles.rapidsrivers

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.json.toJsonNode
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.pipe.mapFirst

fun JsonMessage.demandAll(key: IKey, values: List<BehovType>) {
    demandAll(key.str, values.map(BehovType::name))
}

fun JsonMessage.demandValues(vararg keyAndValuePairs: Pair<IKey, String>) {
    keyAndValuePairs.forEach { (key, value) ->
        demandValue(key.str, value)
    }
}

fun JsonMessage.demand(vararg keyAndParserPairs: Pair<IKey, (JsonElement) -> Any>) {
    val keyStringAndParserPairs = keyAndParserPairs.map { it.mapFirst(IKey::str) }
    validate(JsonMessage::demand, keyStringAndParserPairs)
}

fun JsonMessage.rejectKeys(vararg keys: IKey) {
    val keysAsStr = keys.map(IKey::str).toTypedArray()
    rejectKey(*keysAsStr)
}

fun JsonMessage.requireKeys(vararg keys: IKey) {
    val keysAsStr = keys.map(IKey::str).toTypedArray()
    requireKey(*keysAsStr)
}

fun JsonMessage.require(vararg keyAndParserPairs: Pair<IKey, (JsonElement) -> Any>) {
    val keyStringAndParserPairs = keyAndParserPairs.map { it.mapFirst(IKey::str) }
    validate(JsonMessage::require, keyStringAndParserPairs)
}

fun JsonMessage.interestedIn(vararg keyAndParserPairs: Pair<IKey, (JsonElement) -> Any>) {
    val keyStringAndParserPairs = keyAndParserPairs.map { it.mapFirst(IKey::str) }
    validate(JsonMessage::interestedIn, keyStringAndParserPairs)
}

fun JsonMessage.interestedIn(vararg keys: IKey) {
    val keysAsStr = keys.map(IKey::str).toTypedArray()
    interestedIn(*keysAsStr)
}

fun MessageContext.publish(vararg messageFields: Pair<IKey, JsonElement>): JsonElement =
    messageFields
        .associate { (key, value) -> key.str to value.toJsonNode() }
        .let(JsonMessage::newMessage)
        .toJson()
        .also(::publish)
        .parseJson()

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

fun JsonElement.fromJsonMapOnlyKeys(): Map<Key, JsonElement> =
    fromJsonMapFiltered(Key.serializer())

fun JsonElement.fromJsonMapOnlyDatafelter(): Map<DataFelt, JsonElement> =
    fromJsonMapFiltered(DataFelt.serializer())

fun JsonElement.fromJsonMapAllKeys(): Map<IKey, JsonElement> = fromJsonMapFiltered(Key.serializer()) + fromJsonMapFiltered(
    DataFelt.serializer()
)
