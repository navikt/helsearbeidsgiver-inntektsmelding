package no.nav.helsearbeidsgiver.felles

data class PreutfyltResponse(
    val navn: String,
    val identitetsnummer: String,
    val virksomhetsnavn: String,
    val orgnrUnderenhet: String,
    val fravaersperiode: Map<String, List<MottattPeriode>>,
    val egenmeldingsperioder: List<MottattPeriode>,
    val bruttoinntekt: Long,
    val tidligereinntekt: List<MottattHistoriskInntekt>,
    val behandlingsperiode: MottattPeriode,
    val arbeidsforhold: List<Arbeidsforhold>
)
