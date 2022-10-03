package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import kotlinx.serialization.Serializable

@Serializable
data class MottattHistoriskInntekt(
    val maanedsnavn: String,
    val inntekt: Long
)

@Serializable
data class Inntekt(
    val bruttoInntekt: Long,
    val historisk: List<MottattHistoriskInntekt>
)
