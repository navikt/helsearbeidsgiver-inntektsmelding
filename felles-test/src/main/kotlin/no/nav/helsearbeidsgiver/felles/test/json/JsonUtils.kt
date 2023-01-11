package no.nav.helsearbeidsgiver.felles.test.json

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

private val jsonWhitespaceRegex = Regex("""("(?:\\"|[^"])*")|\s""")

fun String.removeJsonWhitespace(): String =
    replace(jsonWhitespaceRegex, "$1")

object JsonIgnoreUnknown {
    private val jsonBuilder = Json {
        ignoreUnknownKeys = true
    }

    fun <T : Any> fromJson(ds: DeserializationStrategy<T>, json: String): T =
        jsonBuilder.decodeFromString(ds, json)
}
