package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import org.valiktor.Validator

class UtbetalerHeleConstraint : CustomConstraint
fun <E> Validator<E>.Property<Boolean?>.isUtbetalerHele(refusjon: Double?) =
    this.validate(UtbetalerHeleConstraint()) { it == false || (isValidRefusjon(refusjon)) }

fun isValidRefusjon(value: Double?): Boolean {
    if (value == null) {
        return false
    }
    if (value <= 0) {
        return false
    }
    val MAX = 1000000
    if (value > MAX) {
        return false
    }
    return true
}
