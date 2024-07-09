package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.utils.json.toJsonStr

@Serializable
data class ResultJson(
    val success: JsonElement? = null,
    val failure: JsonElement? = null,
) {
    fun toJsonStr(): String = toJsonStr(serializer())
}
