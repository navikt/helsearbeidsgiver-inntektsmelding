package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

class ValidationResponse(
    val errors: List<ValidationError>
)

class ValidationError(
    val property: String,
    val error: String,
    val value: String
)
