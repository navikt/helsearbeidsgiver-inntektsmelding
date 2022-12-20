package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.test.json.tryToJson

fun TestRapid.sendJson(vararg keyValuePairs: Pair<String, JsonElement>) {
    keyValuePairs.toMap()
        .tryToJson()
        .toString()
        .let(this::sendTestMessage)
}
