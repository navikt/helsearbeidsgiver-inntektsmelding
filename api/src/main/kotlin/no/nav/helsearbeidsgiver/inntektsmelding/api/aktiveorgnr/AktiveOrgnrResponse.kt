package no.nav.helsearbeidsgiver.inntektsmelding.api.aktiveorgnr

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.felles.FeilReport

@Serializable
data class AktiveOrgnrResponse(
    val underenheter: List<GyldigUnderenhet>,
    val feilReport: FeilReport? = null
)

@Serializable
data class GyldigUnderenhet(
    val orgnrUnderenhet: String,
    val virksomhetsnavn: String
)
