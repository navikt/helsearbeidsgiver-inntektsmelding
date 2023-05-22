package no.nav.helsearbeidsgiver.felles.test.rapidsrivers

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson

fun TestRapid.sendJson(vararg keyValuePairs: Pair<Key, JsonElement>) {
    keyValuePairs.toMap()
        .mapKeys { (key, _) -> key.str }
        .let(this::sendJson)
}

internal fun TestRapid.sendJson(keyValuePairs: Map<String, JsonElement>) {
    keyValuePairs.toJson()
        .toString()
        .let(this::sendTestMessage)
}

fun TestRapid.firstMessage(): JsonElement =
    inspektør.message(0)
        .toString()
        .parseJson()
