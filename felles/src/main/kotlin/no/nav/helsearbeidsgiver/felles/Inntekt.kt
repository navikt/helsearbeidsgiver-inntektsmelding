package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class MottattHistoriskInntekt(
    val maanedsnavn: String,
    val inntekt: Long
)

@Serializable
data class Inntekt(
    val bruttoInntekt: Long,
    val historisk: List<MottattHistoriskInntekt>
)

@Serializable
data class MottattNaturalytelse(
    val type: String,
    val bortfallsdato: LocalDate,
    val verdi: Long
)
