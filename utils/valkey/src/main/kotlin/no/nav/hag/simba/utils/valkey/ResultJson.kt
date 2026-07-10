package no.nav.hag.simba.utils.valkey

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.utils.json.toJson

@Serializable
data class ResultJson(
    val success: JsonElement? = null,
    val failure: JsonElement? = null,
) {
    fun toJson(): JsonElement = toJson(serializer())
}
