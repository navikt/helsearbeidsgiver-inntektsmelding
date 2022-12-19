package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import kotlinx.serialization.Serializable

@Serializable
data class TrengerInntektResponse(
    val uuid: String,
    val orgnr: String,
    val fnr: String
)
