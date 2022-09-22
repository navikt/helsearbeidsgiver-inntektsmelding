@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.inntektsmelding.api.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class MottattNaturalytelse(
    val type: String,
    val bortfallsdato: LocalDate,
    val verdi: Long
)
