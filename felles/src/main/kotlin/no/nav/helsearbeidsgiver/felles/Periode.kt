@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Serializable
data class Periode(
    val fom: LocalDate,
    val tom: LocalDate
) {
    fun slåSammenIgnorerHelgOrNull(other: Periode): Periode? =
        if (!erSammenhengendeIgnorerHelg(other)) {
            null
        } else {
            Periode(
                minOf(fom, other.fom),
                maxOf(tom, other.tom)
            )
        }

    fun erSammenhengendeIgnorerHelg(other: Periode): Boolean =
        tom.isAdjacentIgnoringWeekend(other.fom) ||
            other.tom.isAdjacentIgnoringWeekend(fom)
}

infix fun LocalDate.til(tom: LocalDate): Periode =
    Periode(
        fom = this,
        tom = tom
    )

private fun LocalDate.isAdjacentIgnoringWeekend(other: LocalDate): Boolean {
    val gapAntallDager = daysUntil(other)
    return when (dayOfWeek) {
        DayOfWeek.FRIDAY -> gapAntallDager in setOf(1L, 2L, 3L)
        DayOfWeek.SATURDAY -> gapAntallDager in setOf(1L, 2L)
        else -> gapAntallDager == 1L
    }
}

private fun LocalDate.daysUntil(other: LocalDate): Long =
    until(other, ChronoUnit.DAYS)
