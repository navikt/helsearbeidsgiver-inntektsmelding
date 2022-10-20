package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import org.valiktor.Validator
import java.time.LocalDate

class BehandlingsPeriodeConstraint : CustomConstraint
fun <E> Validator<E>.Property<LocalDate?>.isBefore(behandlingsdagerTom: LocalDate) =
    this.validate(BehandlingsPeriodeConstraint()) { it?.isBefore(behandlingsdagerTom) ?: false }
