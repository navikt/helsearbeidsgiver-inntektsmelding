package no.nav.helsearbeidsgiver.felles.test.json

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.json.jsonIgnoreUnknown

private val jsonWhitespaceRegex = Regex("""("(?:\\"|[^"])*")|\s""")

fun String.removeJsonWhitespace(): String =
    replace(jsonWhitespaceRegex, "$1")

// TODO fjern når mulig
object JsonIgnoreUnknown {
    fun <T : Any> fromJson(ds: DeserializationStrategy<T>, json: JsonElement): T =
        jsonIgnoreUnknown.decodeFromJsonElement(ds, json)
}
