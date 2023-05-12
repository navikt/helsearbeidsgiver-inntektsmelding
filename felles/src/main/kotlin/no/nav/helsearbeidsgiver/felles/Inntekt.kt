@file:UseSerializers(YearMonthSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.json.serializer.YearMonthSerializer
import no.nav.helsearbeidsgiver.felles.utils.divideMoney
import no.nav.helsearbeidsgiver.felles.utils.sumMoney
import java.time.YearMonth

@Serializable
data class MottattHistoriskInntekt(
    val maaned: YearMonth,
    val inntekt: Double
)

@Serializable
data class Inntekt(
    val historisk: List<MottattHistoriskInntekt>
) {
    fun total(): Double =
        historisk.map { it.inntekt }
            .sumMoney()

    fun gjennomsnitt(): Double =
        if (historisk.isEmpty()) {
            0.0
        } else {
            total().divideMoney(historisk.size)
        }
}
