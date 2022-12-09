@file:UseSerializers(YearMonthSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.serializers.YearMonthSerializer
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

@Serializable
data class MottattHistoriskInntekt(
    val maanedsnavn: YearMonth?,
    val inntekt: Double?
)

@Serializable
data class Inntekt(
    val total: Double,
    val historisk: List<MottattHistoriskInntekt>
) {
    val bruttoInntekt: Double = gjennomsnitt()

    // Beholder navnet bruttoInntekt for nå, er mer et "forslag til bruttoInntekt" som akkurat nå beregnes ved snitt...
    fun gjennomsnitt(): Double {
        if (historisk.size <= 1) return total
        return BigDecimal.valueOf(total).divide(BigDecimal(historisk.size), RoundingMode.HALF_UP).toDouble()
    }

    override fun toString(): String {
        return "Inntekt(total=$total, historisk=$historisk, bruttoInntekt=$bruttoInntekt)"
    }
}
