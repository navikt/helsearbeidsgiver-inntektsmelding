package no.nav.hag.simba.kontrakt.domene.ansettelsesforhold

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import java.time.LocalDate

fun Periode.overlapperMed(annen: Ansettelsesforhold): Boolean {
    val endA = this.tom
    val endB = annen.sluttdato ?: LocalDate.MAX

    return !this.fom.isAfter(endB) && !annen.startdato.isAfter(endA)
}
