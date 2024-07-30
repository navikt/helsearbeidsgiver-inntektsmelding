package no.nav.helsearbeidsgiver.felles.rapidsrivers

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.pipe.mapFirst

fun JsonMessage.toPretty(): String = toJson().parseJson().toPretty()

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

fun JsonMessage.interestedIn(vararg keys: IKey) {
    val keysAsStr = keys.map(IKey::str).toTypedArray()
    interestedIn(*keysAsStr)
}

fun MessageContext.publishNotNull(vararg messageFields: Pair<Key, JsonElement?>): JsonElement = publishNotNull(messageFields.toMap())

fun MessageContext.publish(vararg messageFields: Pair<Key, JsonElement>): JsonElement = publish(messageFields.toMap())

fun MessageContext.publishNotNull(messageFields: Map<Key, JsonElement?>): JsonElement =
    messageFields
        .mapValuesNotNull { it }
        .let(::publish)

fun MessageContext.publish(messageFields: Map<Key, JsonElement>): JsonElement =
    messageFields
        .mapKeys { (key, _) -> key.toString() }
        .filterValues { it !is JsonNull }
        .toJson()
        .toString()
        .let {
            JsonMessage(it, MessageProblems(it), null)
        }.toJson()
        .also(::publish)
        .parseJson()

private fun JsonMessage.validate(
    validateFn: (JsonMessage, String, (JsonNode) -> Any) -> Unit,
    keyAndParserPairs: List<Pair<String, (JsonElement) -> Any>>,
) {
    keyAndParserPairs.forEach { (key, block) ->
        validateFn(this, key) {
            it.toString().parseJson().let(block)
        }
    }
}
