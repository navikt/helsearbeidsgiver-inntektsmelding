package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import org.valiktor.Validator

class IdentitetsnummerConstraint : CustomConstraint
fun <E> Validator<E>.Property<String?>.isIdentitetsnummer() =
    this.validate(IdentitetsnummerConstraint()) { FoedselsNrValidator.isValid(it) }
