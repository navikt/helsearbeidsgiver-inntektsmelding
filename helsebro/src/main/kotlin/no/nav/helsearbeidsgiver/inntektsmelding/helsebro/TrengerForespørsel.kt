@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer
import java.util.UUID

private val jsonBuilder = Json {
    encodeDefaults = true
}

@Serializable
data class TrengerForespørsel(
    val orgnr: String,
    val fnr: String,
    val vedtaksperiodeId: UUID
) {
    val eventType = "TRENGER_FORESPØRSEL"

    fun toJson(): String =
        jsonBuilder.encodeToString(this)
}
