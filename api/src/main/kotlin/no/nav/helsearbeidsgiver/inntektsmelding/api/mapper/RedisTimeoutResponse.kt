package no.nav.helsearbeidsgiver.inntektsmelding.api.mapper

import kotlinx.serialization.Serializable

@Serializable
data class RedisTimeoutResponse(
    var uuid: String,
    var error: String? = "Brukte for lang tid"
)
