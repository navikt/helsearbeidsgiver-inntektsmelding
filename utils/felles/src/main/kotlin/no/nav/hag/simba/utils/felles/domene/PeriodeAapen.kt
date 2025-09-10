@file:UseSerializers(LocalDateSerializer::class)

package no.nav.hag.simba.utils.felles.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class PeriodeAapen(
    val fom: LocalDate,
    val tom: LocalDate?,
)
