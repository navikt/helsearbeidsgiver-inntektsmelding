package no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.felles.json.toJsonElement

fun JsonMessage.demandValue(key: Pri.Key, value: Pri.ValueEnum) {
    demandValue(key.str, value.name)
}

fun JsonMessage.requireKeys(vararg keys: Pri.Key) {
    val keysAsStr = keys.map(Pri.Key::str).toTypedArray()
    this.requireKey(*keysAsStr)
}

fun JsonMessage.require(vararg keys: Pair<Pri.Key, (JsonElement) -> Any>) {
    keys.forEach { (key, block) ->
        this.require(key.str) {
            it.toJsonElement().let(block)
        }
    }
}

fun JsonMessage.value(key: Pri.Key): JsonNode =
    this[key.str]

fun jsonOf(vararg keyValuePairs: Pair<Pri.Key, JsonElement>): JsonElement =
    keyValuePairs.toMap()
        .mapKeys { (key, _) -> key.str }
        .let(Json::encodeToJsonElement)
