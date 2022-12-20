package no.nav.helsearbeidsgiver.felles.test.rapidsrivers

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.test.json.tryToJson

fun TestRapid.sendJson(vararg keyValuePairs: Pair<Key, JsonElement>) {
    keyValuePairs.toMap()
        .mapKeys { (key, _) -> key.str }
        .tryToJson()
        .toString()
        .let(this::sendTestMessage)
}

fun TestRapid.lastMessageJson(): String =
    inspektør.message(0).toString()
