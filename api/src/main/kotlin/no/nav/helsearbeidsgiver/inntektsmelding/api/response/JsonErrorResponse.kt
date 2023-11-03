package no.nav.helsearbeidsgiver.inntektsmelding.api.response

import kotlinx.serialization.Serializable

@Serializable
data class JsonErrorResponse(
    val forespoerselId: String
) {
    val error = "Feil under serialisering."
}
