package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemtlagreimservice

import no.nav.hag.simba.utils.felles.domene.PeriodeAapen
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode

private const val MAKS_DAGER_OPPHOLD = 3L

fun List<Periode>.aktivtArbeidsforholdIPeriode(ansettelsesperioder: Set<PeriodeAapen>): Boolean {
    val ansattPerioderSammenslaatt = slaaSammenPerioder(ansettelsesperioder)
    return this.any { it.innenforArbeidsforhold(ansattPerioderSammenslaatt) } || this.any { it.innenforArbeidsforhold(ansettelsesperioder) }
}

private fun Periode.innenforArbeidsforhold(ansattPerioder: Set<PeriodeAapen>): Boolean =
    ansattPerioder.any { ansPeriode ->
        !fom.isBefore(ansPeriode.fom) &&
            (ansPeriode.tom == null || !tom.isAfter(ansPeriode.tom))
    }

private fun slaaSammenPerioder(ansettelsesperioder: Set<PeriodeAapen>): Set<PeriodeAapen> {
    if (ansettelsesperioder.size <= 1) return ansettelsesperioder

    val remainingPeriods =
        ansettelsesperioder
            .sortedBy { it.fom }
            .toMutableList()

    val merged = ArrayList<PeriodeAapen>()

    do {
        var currentPeriod = remainingPeriods[0]
        remainingPeriods.removeAt(0)

        do {
            val connectedPeriod =
                remainingPeriods
                    .find { !oppholdMellomPerioderOverstigerDager(currentPeriod, it) }
            if (connectedPeriod != null) {
                currentPeriod = PeriodeAapen(currentPeriod.fom, connectedPeriod.tom)
                remainingPeriods.remove(connectedPeriod)
            }
        } while (connectedPeriod != null)

        merged.add(currentPeriod)
    } while (remainingPeriods.isNotEmpty())

    return merged.toSet()
}

private fun oppholdMellomPerioderOverstigerDager(
    a1: PeriodeAapen,
    a2: PeriodeAapen,
): Boolean = a1.tom?.plusDays(MAKS_DAGER_OPPHOLD)?.isBefore(a2.fom) ?: true
