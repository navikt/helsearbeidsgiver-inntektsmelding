package no.nav.helsearbeidsgiver.felles.rapidsrivers

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty

fun JsonMessage.toPretty(): String = toJson().parseJson().toPretty()

fun JsonMessage.demandValues(vararg keyAndValuePairs: Pair<IKey, String>) {
    keyAndValuePairs.forEach { (key, value) ->
        demandValue(key.str, value)
    }
}

fun JsonMessage.rejectKeys(vararg keys: IKey) {
    val keysAsStr = keys.map(IKey::str).toTypedArray()
    rejectKey(*keysAsStr)
}

fun JsonMessage.requireKeys(vararg keys: IKey) {
    val keysAsStr = keys.map(IKey::str).toTypedArray()
    requireKey(*keysAsStr)
}

fun MessageContext.publish(vararg messageFields: Pair<Key, JsonElement>): JsonElement = publish(messageFields.toMap())

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
