package no.nav.helsearbeidsgiver.inntektsmelding.syk

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class Syk(
    val fravaersperiode: Map<String, List<MottattPeriode>>,
    val behandlingsperiode: MottattPeriode
)

@Serializable
data class MottattPeriode(
    val fra: LocalDate,
    val til: LocalDate
)
