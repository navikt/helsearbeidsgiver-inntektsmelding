package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val jsonBuilder = Json {
    encodeDefaults = true
}

@Serializable
data class TrengerForespurtData(
    val orgnr: String,
    val fnr: String
) {
    val eventType = "TRENGER_FORESPURT_DATA"

    fun toJson(): String =
        jsonBuilder.encodeToString(this)
}
