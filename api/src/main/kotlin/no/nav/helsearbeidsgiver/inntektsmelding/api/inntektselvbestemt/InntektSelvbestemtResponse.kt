package no.nav.helsearbeidsgiver.inntektsmelding.api.inntektselvbestemt

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.felles.InntektPerMaaned

@Serializable
data class InntektSelvbestemtResponse(
    val bruttoinntekt: Double,
    val tidligereInntekter: List<InntektPerMaaned>,
)
