package no.nav.helsearbeidsgiver.inntektsmelding.api.dto

data class MottattArbeidsforhold(
    val arbeidsforholdId: String,
    val arbeidsforhold: String,
    val stillingsprosent: Number
)
