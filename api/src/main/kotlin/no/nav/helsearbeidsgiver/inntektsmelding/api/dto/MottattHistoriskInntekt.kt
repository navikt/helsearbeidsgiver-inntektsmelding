package no.nav.helsearbeidsgiver.inntektsmelding.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class MottattHistoriskInntekt(
    val maanedsnavn: String,
    val inntekt: Number
)
