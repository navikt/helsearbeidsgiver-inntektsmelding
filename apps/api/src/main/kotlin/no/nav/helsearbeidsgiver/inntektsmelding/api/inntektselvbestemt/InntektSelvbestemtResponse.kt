@file:UseSerializers(YearMonthSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.inntektselvbestemt

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.YearMonthSerializer
import java.time.YearMonth

@Serializable
data class InntektSelvbestemtResponse(
    val gjennomsnitt: Double,
    val historikk: Map<YearMonth, Double?>,
)
