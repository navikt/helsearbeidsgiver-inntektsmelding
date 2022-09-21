package no.nav.helsearbeidsgiver.inntektsmelding.api.dto

import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class MottattNaturalytelse(
    val type: String,
    val bortfallsdato: LocalDate,
    val verdi: Number
)
