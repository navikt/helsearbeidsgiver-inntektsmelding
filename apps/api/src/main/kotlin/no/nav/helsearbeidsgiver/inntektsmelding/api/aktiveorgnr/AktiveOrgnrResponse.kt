package no.nav.helsearbeidsgiver.inntektsmelding.api.aktiveorgnr

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

@Serializable
data class AktiveOrgnrResponse(
    val fulltNavn: String? = null,
    val avsenderNavn: String,
    val underenheter: List<GyldigUnderenhet>,
)

@Serializable
data class GyldigUnderenhet(
    val orgnrUnderenhet: Orgnr,
    val virksomhetsnavn: String,
)
