package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.Naturalytelse
import org.valiktor.Validator

class NaturalytelserConstraint : CustomConstraint

fun <E> Validator<E>.Property<Iterable<Naturalytelse>?>.isValidNaturalytelser() {
    this.validate(NaturalytelserConstraint()) { isAllNaturalytelserValid(it) } // TODO Må iterere gjennom alle
}

fun isAllNaturalytelserValid(naturalytelser: Iterable<Naturalytelse>?): Boolean {
    if (naturalytelser == null) {
        return false
    }
    naturalytelser.forEach {
        if (it.beløp < 0) {
            return false
        }
        if (it.beløp > 1000000) {
            return false
        }
    }
    return true
}
