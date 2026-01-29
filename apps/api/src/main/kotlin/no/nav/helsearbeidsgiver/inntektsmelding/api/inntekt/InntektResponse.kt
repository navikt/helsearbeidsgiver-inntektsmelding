@file:UseSerializers(YearMonthSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.YearMonthSerializer
import java.time.YearMonth

@Serializable
data class InntektResponse(
    val gjennomsnitt: Double,
    val historikk: Map<YearMonth, Double?>,
)
