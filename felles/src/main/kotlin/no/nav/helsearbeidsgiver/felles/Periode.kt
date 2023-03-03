@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class Periode(
    val fom: LocalDate,
    val tom: LocalDate
) {
    fun erSammenhengende(other: Periode): Boolean {
        return this.tom.plusDays(1) == other.fom ||
            this.fom.minusDays(1) == other.tom
        // TODO: Helger
    }

    fun slåSammenOrNull(other: Periode): Periode? =
        if (!erSammenhengende(other)) {
            null
        } else {
            Periode(
                minOf(fom, other.fom),
                maxOf(tom, other.tom)
            )
        }
}

infix fun LocalDate.til(tom: LocalDate): Periode =
    Periode(
        fom = this,
        tom = tom
    )
