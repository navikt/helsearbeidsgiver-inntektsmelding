package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import org.valiktor.Constraint

sealed interface CustomConstraint : Constraint {
    override val messageBundle: String
        get() = "messages"
}

object FeilmeldingConstraint : CustomConstraint
