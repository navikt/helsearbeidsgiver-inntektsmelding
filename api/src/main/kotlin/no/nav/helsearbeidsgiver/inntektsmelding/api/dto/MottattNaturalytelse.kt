package no.nav.helsearbeidsgiver.inntektsmelding.api.dto

data class MottattNaturalytelse(
    val type: String,
    val bortfallsdato: String,
    val verdi: Number
)
