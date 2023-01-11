package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

@Serializable
data class TrengerInntekt(
    val fnr: String,
    val orgnr: String,
    val sykemeldingsperioder: List<Periode> = emptyList(),
    val egenmeldingsperioder: List<Periode> = emptyList()
)
