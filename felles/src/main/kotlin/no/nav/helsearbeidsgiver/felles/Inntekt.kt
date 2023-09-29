@file:UseSerializers(YearMonthSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.utils.divideMoney
import no.nav.helsearbeidsgiver.felles.utils.sumMoney
import no.nav.helsearbeidsgiver.utils.json.serializer.YearMonthSerializer
import java.time.YearMonth

@Serializable
data class InntektPerMaaned(
    val maaned: YearMonth,
    val inntekt: Double?
)

@Serializable
data class Inntekt(
    val maanedOversikt: List<InntektPerMaaned>
) {
    fun total(): Double =
        maanedOversikt.mapNotNull { it.inntekt }
            .sumMoney()

    fun gjennomsnitt(): Double =
        if (maanedOversikt.isEmpty()) {
            0.0
        } else {
            total().divideMoney(3) // Del alltid inntekt på tre måneder
        }
}
