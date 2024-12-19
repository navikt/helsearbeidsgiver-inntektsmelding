package no.nav.helsearbeidsgiver.felles.test.rapidsrivers

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson

fun <K : IKey> TestRapid.sendJson(vararg messageFields: Pair<K, JsonElement>) {
    sendJson(messageFields.toMap())
}

fun <K : IKey> TestRapid.sendJson(messageFields: Map<K, JsonElement>) {
    messageFields
        .mapKeys { (key, _) -> key.toString() }
        .toJson()
        .toString()
        .let(this::sendTestMessage)
}

fun TestRapid.firstMessage(): JsonElement = message(0)

fun TestRapid.message(index: Int): JsonElement =
    inspektør
        .message(index)
        .toString()
        .parseJson()
