package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import java.time.YearMonth

@Serializable
data class MottattHistoriskInntekt(
    val maanedsnavn: YearMonth?,
    val inntekt: Double?
)

@Serializable
data class Inntekt(
    val bruttoInntekt: Double,
    val historisk: List<MottattHistoriskInntekt>
)
