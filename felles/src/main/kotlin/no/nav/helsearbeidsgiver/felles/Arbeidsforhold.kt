@file:UseSerializers(LocalDateSerializer::class, LocalDateTimeSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateTimeSerializer
import java.time.LocalDate
import java.time.LocalDateTime

@Serializable
data class Arbeidsforhold(
    val arbeidsgiver: Arbeidsgiver,
    val ansettelsesperiode: Ansettelsesperiode,
    val registrert: LocalDateTime
)

@Serializable
data class Ansettelsesperiode(
    val periode: PeriodeNullable
)

@Serializable
data class Arbeidsgiver(
    val type: String,
    val organisasjonsnummer: String?
)

@Serializable
data class PeriodeNullable(
    val fom: LocalDate?,
    val tom: LocalDate? = null
)
