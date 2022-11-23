package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import org.valiktor.Validator
import java.time.LocalDate

object BehandlingsdagerConstraint : CustomConstraint

fun <E> Validator<E>.Property<Iterable<LocalDate>?>.isValidBehandlingsdager() {
    this.validate(BehandlingsdagerConstraint) { isValidBehandlingsdager(it?.toList() ?: emptyList()) }
}
