package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemtlagreimservice

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.felles.domene.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.domene.PeriodeNullable

private const val MAKS_DAGER_OPPHOLD = 3L

fun List<Periode>.aktivtArbeidsforholdIPeriode(arbeidsforhold: List<Arbeidsforhold>): Boolean {
    val ansattPerioder = arbeidsforhold.map { it.ansettelsesperiode.periode }
    val ansattPerioderSammenslaatt = slaaSammenPerioder(ansattPerioder)
    return this.any { it.innenforArbeidsforhold(ansattPerioderSammenslaatt) } || this.any { it.innenforArbeidsforhold(ansattPerioder) }
}

private fun Periode.innenforArbeidsforhold(ansattPerioder: List<PeriodeNullable>): Boolean =
    ansattPerioder.any { ansPeriode ->
        (ansPeriode.tom == null || this.tom.isBefore(ansPeriode.tom) || this.tom == ansPeriode.tom) &&
            (ansPeriode.fom!!.isBefore(this.fom) || ansPeriode.fom!!.isEqual(this.fom))
    }

private fun slaaSammenPerioder(list: List<PeriodeNullable>): List<PeriodeNullable> {
    if (list.size < 2) return list

    val remainingPeriods =
        list
            .sortedBy { it.fom }
            .toMutableList()

    val merged = ArrayList<PeriodeNullable>()

    do {
        var currentPeriod = remainingPeriods[0]
        remainingPeriods.removeAt(0)

        do {
            val connectedPeriod =
                remainingPeriods
                    .find { !oppholdMellomPerioderOverstigerDager(currentPeriod, it, MAKS_DAGER_OPPHOLD) }
            if (connectedPeriod != null) {
                currentPeriod = PeriodeNullable(currentPeriod.fom, connectedPeriod.tom)
                remainingPeriods.remove(connectedPeriod)
            }
        } while (connectedPeriod != null)

        merged.add(currentPeriod)
    } while (remainingPeriods.isNotEmpty())

    return merged
}

private fun oppholdMellomPerioderOverstigerDager(
    a1: PeriodeNullable,
    a2: PeriodeNullable,
    dager: Long,
): Boolean = a1.tom?.plusDays(dager)?.isBefore(a2.fom) ?: true
