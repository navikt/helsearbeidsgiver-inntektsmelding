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
    /*
        Sjekker om perioder overlapper. Er foreløpig ikke i bruk for sykmeldinger,
        da disse visstnok ikke kan overlappe
     */
    fun overlapper(other: Periode): Boolean {
        return this == other || (fom.isBefore(other.tom) && tom.isAfter(other.fom)) ||
            other.fom == tom || fom == other.tom
    }

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
