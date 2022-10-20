package no.nav.helsearbeidsgiver.inntektsmelding.api.validation // ktlint-disable filename

import org.valiktor.Validator
import java.time.LocalDate

class OpphørerRefusjonskravetConstraint : CustomConstraint
fun <E> Validator<E>.Property<Boolean?>.isOpphørerValid(sisteDag: LocalDate?) =
    this.validate(OpphørerRefusjonskravetConstraint()) { !(it == true && sisteDag == null) }
