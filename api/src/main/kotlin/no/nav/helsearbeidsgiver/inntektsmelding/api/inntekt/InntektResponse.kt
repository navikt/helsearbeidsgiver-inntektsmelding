package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.felles.FeilReport
import no.nav.helsearbeidsgiver.felles.InntektPerMaaned

@Serializable
data class InntektResponse(
    val resultat: InntektResultat? = null,
    val feilReport: FeilReport? = null
)

@Serializable
data class InntektResultat(
    val bruttoinntekt: Double,
    val tidligereInntekter: List<InntektPerMaaned>
)
