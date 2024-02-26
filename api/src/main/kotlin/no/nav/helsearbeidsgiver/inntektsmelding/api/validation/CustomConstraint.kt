package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import org.valiktor.Constraint

interface CustomConstraint : Constraint {
    override val messageBundle: String
        get() = "messages"
}
