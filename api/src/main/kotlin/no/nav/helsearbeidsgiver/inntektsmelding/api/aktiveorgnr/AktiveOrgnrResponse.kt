package no.nav.helsearbeidsgiver.inntektsmelding.api.aktiveorgnr

import kotlinx.serialization.Serializable

@Serializable
data class AktiveOrgnrResponse(
    val fulltNavn: String? = null,
    val underenheter: List<GyldigUnderenhet>
)

@Serializable
data class GyldigUnderenhet(
    val orgnrUnderenhet: String,
    val virksomhetsnavn: String
)
