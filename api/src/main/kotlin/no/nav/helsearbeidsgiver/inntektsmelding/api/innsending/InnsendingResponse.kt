package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import kotlinx.serialization.Serializable

@Serializable
data class InnsendingResponse(
    var uuid: String
)
