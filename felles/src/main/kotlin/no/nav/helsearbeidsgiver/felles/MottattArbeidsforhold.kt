package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

@Serializable
data class MottattArbeidsforhold(
    val arbeidsforholdId: String,
    val arbeidsforhold: String,
    val stillingsprosent: Float
)
