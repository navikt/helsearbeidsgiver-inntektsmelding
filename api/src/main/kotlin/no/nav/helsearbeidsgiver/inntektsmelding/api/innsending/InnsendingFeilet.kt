package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import kotlinx.serialization.Serializable

@Serializable
data class InnsendingFeilet(
    var uuid: String,
    var error: String
)
