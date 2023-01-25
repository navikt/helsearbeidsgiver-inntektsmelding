package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.felles.Periode

@Serializable
data class TrengerInntektResponse(
    val uuid: String,
    val orgnr: String,
    val fnr: String,
    val sykmeldingsperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>
)
