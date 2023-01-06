package no.nav.helsearbeidsgiver.felles

import java.time.LocalDate

data class PreutfyltResponse(
    val navn: String,
    val identitetsnummer: String,
    val orgnrUnderenhet: String,
    val fravaersperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>,
    val bruttoinntekt: Double?,
    val tidligereinntekter: List<MottattHistoriskInntekt>,
    val behandlingsperiode: Periode?,
    val behandlingsdager: List<LocalDate>
)
