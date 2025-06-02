@file:UseSerializers(YearMonthSerializer::class)

package no.nav.helsearbeidsgiver.felles.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.YearMonthSerializer
import java.time.YearMonth

@Serializable
data class InntektPerMaaned(
    val maaned: YearMonth,
    val inntekt: Double?,
)
