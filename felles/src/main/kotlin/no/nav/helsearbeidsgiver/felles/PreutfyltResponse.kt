package no.nav.helsearbeidsgiver.felles

data class PreutfyltResponse(
    val navn: String,
    val identitetsnummer: String,
    val orgnrUnderenhet: String,
    val fravaersperioder: Map<String, List<MottattPeriode>>,
    val egenmeldingsperioder: List<MottattPeriode>,
    val bruttoinntekt: Double?,
    val tidligereinntekter: List<MottattHistoriskInntekt>,
    val behandlingsperiode: MottattPeriode
)
