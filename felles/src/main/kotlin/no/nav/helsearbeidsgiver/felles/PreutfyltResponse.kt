package no.nav.helsearbeidsgiver.felles

data class PreutfyltResponse(
    val navn: String,
    val identitetsnummer: String,
    val orgnrUnderenhet: String,
    val fravaersperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>,
    val bruttoinntekt: Double?,
    val tidligereinntekter: List<MottattHistoriskInntekt>,
    val behandlingsperiode: Periode
)
