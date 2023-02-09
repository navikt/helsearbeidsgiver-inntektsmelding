@file:UseSerializers(LocalDateSerializer::class, LocalDateTimeSerializer::class)

package no.nav.helsearbeidsgiver.felles.inntektsmelding.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateTimeSerializer
import java.time.LocalDate

@Serializable
data class RefusjonEndring(
    val beløp: Double,
    val dato: LocalDate
)
