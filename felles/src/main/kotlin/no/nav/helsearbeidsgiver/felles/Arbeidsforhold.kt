package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

@Serializable
data class Arbeidsforhold(
    val arbeidsforholdId: String, // fnr eller arbeidsforholdId?
    val arbeidsforhold: String,
    val stillingsprosent: Float
)
