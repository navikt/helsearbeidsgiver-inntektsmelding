package no.nav.helsearbeidsgiver.felles.test.rapidsrivers

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson

fun TestRapid.sendJson(vararg keyValuePairs: Pair<IKey, JsonElement>) {
    keyValuePairs.toMap()
        .mapKeys { (key, _) -> key.str }
        .let(this::sendJson)
}

fun TestRapid.sendJson(keyValuePairs: Map<String, JsonElement>) {
    keyValuePairs.toJson()
        .toString()
        .let(this::sendTestMessage)
}

fun TestRapid.firstMessage(): JsonElement =
    inspektør.message(0)
        .toString()
        .parseJson()
