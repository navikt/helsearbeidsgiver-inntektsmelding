package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

@Serializable
data class TrengerInntekt(
    val fnr: String,
    val orgnr: String,
    val sykmeldingsperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>,
    val forespurtData: ForespurtData
)
