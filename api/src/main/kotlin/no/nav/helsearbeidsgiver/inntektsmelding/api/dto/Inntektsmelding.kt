package no.nav.helsearbeidsgiver.inntektsmelding.api.dto

data class Inntektsmelding(
    val navn: String,
    val identitetsnummer: String,
    val virksomhetsnavn: String,
    val orgnrUnderenhet: String,
    val fravaersperiode: List<MottattPeriode>,
    val egenmeldingsperioder: List<MottattPeriode>,
    val bruttoinntekt: Number,
    val tidligereinntekt: List<MottattHistoriskInntekt>,
    val behandlingsdager: List<String>,
    val behandlingsperiode: MottattPeriode,
    val arbeidsforhold: List<MottattArbeidsforhold>
)
