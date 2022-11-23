package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

@Serializable
data class Syk(
    val fravaersperiode: List<MottattPeriode>,
    val behandlingsperiode: MottattPeriode
)
