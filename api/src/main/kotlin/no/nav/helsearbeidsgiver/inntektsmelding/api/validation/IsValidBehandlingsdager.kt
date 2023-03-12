package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import java.time.LocalDate
import java.time.temporal.WeekFields

/**
 * Angi de 12 dager som den ansatte vært borte fra jobbet for behandling.
 * Det kan kun være en behandlingsdag per uke.
 * I tillegg kan det ikke være mer enn 15 dager mellom to behandlinger.
 **/
// @TODO dene testen feiler på søndag. Er dette riktig ?
fun isValidBehandlingsdager(behandlingsdager: List<LocalDate>): Boolean {
    if (behandlingsdager.isEmpty() || behandlingsdager.size == 1) {
        return true
    }
    if (behandlingsdager.size > 12) {
        return false
    }
    val dager = behandlingsdager.sorted()
    for (i in 1..dager.size - 1) {
        val forrige = dager.get(i - 1)
        val forrigePlus15 = forrige.plusDays(15)
        val neste = dager.get(i)
        if (neste.isAfter(forrigePlus15)) {
            return false
        }
        if (neste.equals(forrige)) {
            return false
        }
        val forrigeUke = forrige.get(WeekFields.ISO.weekOfYear())
        val nesteUke = neste.get(WeekFields.ISO.weekOfYear())
        if (forrigeUke.equals(nesteUke)) {
            return false
        }
    }
    return true
}
