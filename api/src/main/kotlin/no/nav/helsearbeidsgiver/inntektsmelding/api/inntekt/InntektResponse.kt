package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.felles.MottattHistoriskInntekt

@Serializable
data class InntektResponse(
    val bruttoinntekt: Double,
    val tidligereInntekter: List<MottattHistoriskInntekt>
)
