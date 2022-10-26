package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.felles.serializers.YearMonthSerializer
import java.time.YearMonth

@Serializable
data class MottattHistoriskInntekt(
    @Serializable(YearMonthSerializer::class)
    val maanedsnavn: YearMonth?,
    val inntekt: Double?
)

@Serializable
data class Inntekt(
    val bruttoInntekt: Double,
    val historisk: List<MottattHistoriskInntekt>
)
