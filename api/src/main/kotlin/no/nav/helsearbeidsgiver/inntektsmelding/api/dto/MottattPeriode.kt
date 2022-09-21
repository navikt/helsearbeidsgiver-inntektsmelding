package no.nav.helsearbeidsgiver.inntektsmelding.api.dto

import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class MottattPeriode(
    val fra: LocalDate,
    val til: LocalDate
)
