package no.nav.helsearbeidsgiver.inntektsmelding.api

data class DummyConstraintViolation(
    val property: String,
    val value: Any? = null,
    val constraint: DummyConstraint
)

data class DummyConstraint(
    val name: String,
    val messageBundle: String,
    val messageKey: String
)
