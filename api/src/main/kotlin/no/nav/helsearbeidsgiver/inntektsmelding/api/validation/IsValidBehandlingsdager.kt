package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import java.time.LocalDate

/**
 * Angi de 12 dager som den ansatte vært borte fra jobbet for behandling.
 * Det kan kun være en behandlingsdag per uke.
 * I tillegg kan det ikke være mer enn 15 dager mellom to behandlinger.
 **/
fun isValidBehandlingsdager(behandlingsdager: List<LocalDate>): Boolean {
    if (behandlingsdager.isEmpty()) {
        return true
    }
    if (behandlingsdager.size > 12) {
        return false
    }
    // TODO Validering av regler for behandlingsdager
    return true
}
