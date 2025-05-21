@file:UseSerializers(YearMonthSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.inntektselvbestemt

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.domene.InntektPerMaaned
import no.nav.helsearbeidsgiver.utils.json.serializer.YearMonthSerializer
import java.time.YearMonth

@Serializable
data class InntektSelvbestemtResponse(
    val gjennomsnitt: Double,
    val historikk: Map<YearMonth, Double?>,
    // TODO utdaterte felt, slett etter overgangsperiode i frontend
    val bruttoinntekt: Double,
    val tidligereInntekter: List<InntektPerMaaned>,
)
