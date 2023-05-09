package no.nav.helsearbeidsgiver.inntektsmelding.api.response

import kotlinx.serialization.Serializable

@Serializable
data class JacksonErrorResponse(
    val uuid: String
) {
    val error = "Feil under serialisering med jackson."
}
