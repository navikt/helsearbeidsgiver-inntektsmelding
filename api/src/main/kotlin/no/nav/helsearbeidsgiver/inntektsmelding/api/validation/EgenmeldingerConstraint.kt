package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.Egenmelding
import org.valiktor.Validator

class EgenmeldingerConstraint : CustomConstraint

fun <E> Validator<E>.Property<Iterable<Egenmelding>?>.isValidEgenmeldinger() {
    this.validate(EgenmeldingerConstraint()) { isValidEgenmeldingerListe(it) }
}

fun isValidEgenmeldingerListe(egenmeldinger: Iterable<Egenmelding>?): Boolean {
    // TODO Må iterere gjennom alle
    if (egenmeldinger == null) {
        return true
    }
    egenmeldinger.forEach {
        if (!it.fom.isBefore(it.tom)) {
            return false
        }
    }
    return true
}
