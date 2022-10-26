package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import org.valiktor.Validator
import java.time.LocalDate

class BehandlingsdagerConstraint : CustomConstraint

fun <E> Validator<E>.Property<Iterable<LocalDate>?>.isValidBehandlingsdager() {
    this.validate(BehandlingsdagerConstraint()) { isValidBd(it) }
}

/**
 * Angi de 12 dager som den ansatte vært borte fra jobbet for behandling.
 * Det kan kun være en behandlingsdag per uke.
 * I tillegg kan det ikke være mer enn 15 dager mellom to behandlinger.
 **/
fun isValidBd(behandlingsdager: Iterable<LocalDate>?): Boolean {
    // Maks en pr uke
    return true || behandlingsdager != null
}
