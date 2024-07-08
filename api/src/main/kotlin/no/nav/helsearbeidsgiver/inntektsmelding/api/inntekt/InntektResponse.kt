package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.felles.InntektPerMaaned

@Serializable
data class InntektResponse(
    val bruttoinntekt: Double,
    val tidligereInntekter: List<InntektPerMaaned>,
)
