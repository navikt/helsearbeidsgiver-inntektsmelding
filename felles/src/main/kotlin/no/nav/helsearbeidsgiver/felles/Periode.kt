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
    fun overlapper(other: Periode): Boolean {
        return fom.equals(other) || (fom.isBefore(other.tom) && tom.isAfter(other.fom))
    } // TODO: Helger / en dags ikke-gap / fom på samme dag som other.tom
}

infix fun LocalDate.til(tom: LocalDate): Periode =
    Periode(
        fom = this,
        tom = tom
    )
