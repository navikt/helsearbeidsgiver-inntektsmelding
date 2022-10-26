package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import org.valiktor.Constraint
import org.valiktor.Validator

sealed interface CustomConstraint : Constraint {
    override val messageBundle: String
        get() = "validation-messages"
}

object FeilmeldingConstraint : CustomConstraint
fun <E> Validator<E>.Property<String?>.isError() =
    this.validate(FeilmeldingConstraint) { false }

object IdentitetsnummerConstraint : CustomConstraint
fun <E> Validator<E>.Property<String?>.isValidIdentitetsnummer() =
    this.validate(IdentitetsnummerConstraint) { FoedselsNrValidator.isValid(it) }

object OrganisasjonsnummerConstraint : CustomConstraint
fun <E> Validator<E>.Property<String?>.isValidOrganisasjonsnummer() =
    this.validate(OrganisasjonsnummerConstraint) { OrganisasjonsnummerValidator.isValid(it) }
