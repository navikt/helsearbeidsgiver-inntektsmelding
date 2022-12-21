package no.nav.helsearbeidsgiver.felles.test.rapidsrivers.pritopic

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson

fun TestRapid.sendJson(vararg keyValuePairs: Pair<Pri.Key, JsonElement>) {
    keyValuePairs.toMap()
        .mapKeys { (key, _) -> key.str }
        .let(this::sendJson)
}
