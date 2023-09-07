package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import org.valiktor.Validator

object TelefonnummerConstraint : CustomConstraint
fun <E> Validator<E>.Property<String?>.isTelefonnummer() =
    this.validate(TelefonnummerConstraint) {
        TelefonnummerValidator.isValid(it)
    }
