package no.nav.helsearbeidsgiver.inntektsmelding.api.dto

import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class PreutfyltResponse(
    val navn: String,
    val identitetsnummer: String,
    val virksomhetsnavn: String,
    val orgnrUnderenhet: String,
    val fravaersperiode: List<MottattPeriode>,
    val egenmeldingsperioder: List<MottattPeriode>,
    val bruttoinntekt: Number,
    val tidligereinntekt: List<MottattHistoriskInntekt>,
    val behandlingsdager: List<LocalDate>,
    val behandlingsperiode: MottattPeriode,
    val arbeidsforhold: List<MottattArbeidsforhold>
)
