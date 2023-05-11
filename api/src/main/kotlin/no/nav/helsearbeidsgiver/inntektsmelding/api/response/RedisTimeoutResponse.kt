package no.nav.helsearbeidsgiver.inntektsmelding.api.response

import kotlinx.serialization.Serializable

@Serializable
data class RedisTimeoutResponse(
    val uuid: String
) {
    val error = "Brukte for lang tid mot redis."
}
