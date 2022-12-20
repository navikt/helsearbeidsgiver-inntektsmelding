package no.nav.helsearbeidsgiver.felles.test.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

private val jsonWhitespaceRegex = Regex("""("(?:\\"|[^"])*")|\s""")

fun String.removeJsonWhitespace(): String =
    replace(jsonWhitespaceRegex, "$1")

/** Obs! Denne kan feile runtime. */
inline fun <reified T : Any> T.tryToJson(): JsonElement =
    Json.encodeToJsonElement(this)
