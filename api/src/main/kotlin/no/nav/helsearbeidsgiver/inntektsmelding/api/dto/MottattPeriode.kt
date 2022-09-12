package no.nav.helsearbeidsgiver.inntektsmelding.api.dto

import java.time.LocalDate

data class MottattPeriode(
    val fra: LocalDate,
    val til: LocalDate
)
