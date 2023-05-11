package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import kotlinx.serialization.Serializable

@Serializable
class ValidationResponse(
    val errors: List<ValidationError>
)

@Serializable
class ValidationError(
    val property: String,
    val error: String,
    val value: String
)
