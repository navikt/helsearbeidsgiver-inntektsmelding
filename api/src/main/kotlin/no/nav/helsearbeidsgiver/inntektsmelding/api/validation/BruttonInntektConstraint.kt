package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import org.valiktor.Validator

class BruttonInntektConstraint : CustomConstraint
fun <E> Validator<E>.Property<Double?>.isValidBrutto() =
    this.validate(BruttonInntektConstraint()) { isBrutto(it) }

fun isBrutto(value: Double?): Boolean {
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

class BekreftKorrektInntektConstraint : CustomConstraint
fun <E> Validator<E>.Property<Boolean?>.isBekreftetInntekt() =
    this.validate(BekreftKorrektInntektConstraint()) { it == true }
