package no.nav.helsearbeidsgiver.felles

import java.time.YearMonth

data class MottattHistoriskInntekt(
    val maanedsnavn: YearMonth?,
    val inntekt: Double?
)

data class Inntekt(
    val bruttoInntekt: Double,
    val historisk: List<MottattHistoriskInntekt>
)
