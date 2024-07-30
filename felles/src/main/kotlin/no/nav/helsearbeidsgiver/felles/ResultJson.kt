package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ResultJson(
    val success: JsonElement? = null,
    val failure: JsonElement? = null,
)
