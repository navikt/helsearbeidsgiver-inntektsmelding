package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import java.time.LocalDate
import java.time.Month

private val defaultAar = 2018

fun TestRapid.sendJson(vararg keyValuePairs: Pair<String, JsonElement>) {
    keyValuePairs.toMap()
        .tryToJson()
        .toString()
        .let(this::sendTestMessage)
}

/** Obs! Denne kan feile runtime. */
inline fun <reified T : Any> T.tryToJson(): JsonElement =
    Json.encodeToJsonElement(this)

val Int.januar
    get() =
        this.januar(defaultAar)

fun Int.januar(aar: Int): LocalDate =
    LocalDate.of(aar, Month.JANUARY, this)
