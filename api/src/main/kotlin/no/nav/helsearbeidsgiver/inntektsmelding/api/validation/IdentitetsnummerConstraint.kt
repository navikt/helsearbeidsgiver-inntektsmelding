package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import org.valiktor.Validator

object IdentitetsnummerConstraint : CustomConstraint

fun <E> Validator<E>.Property<String?>.isIdentitetsnummer() =
    this.validate(IdentitetsnummerConstraint) {
        require(it != null)
        Fnr.erGyldig(it)
    }
