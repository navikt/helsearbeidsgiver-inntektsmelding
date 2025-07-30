package no.nav.helsearbeidsgiver.felles.rr.test

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.mockk.every
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.rr.Publisher
import no.nav.helsearbeidsgiver.felles.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.rr.river.createRapid
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic

fun mockConnectToRapid(
    testRapid: RapidsConnection,
    rivers: (Publisher) -> List<ObjectRiver<*, *>>,
) = mockStatic(::createRapid) {
    every { createRapid() } returns testRapid
    ObjectRiver.connectToRapid {
        rivers(it)
    }
}

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
