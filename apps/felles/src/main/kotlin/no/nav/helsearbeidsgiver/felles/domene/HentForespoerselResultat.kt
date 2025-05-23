package no.nav.helsearbeidsgiver.felles.domene

import kotlinx.serialization.Serializable

@Serializable
data class HentForespoerselResultat(
    val sykmeldtNavn: String?,
    val avsenderNavn: String?,
    val orgNavn: String?,
    val inntekt: Inntekt?,
    val forespoersel: Forespoersel,
)
