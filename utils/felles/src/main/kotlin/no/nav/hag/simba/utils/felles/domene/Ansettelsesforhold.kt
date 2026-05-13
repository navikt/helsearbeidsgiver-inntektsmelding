@file:UseSerializers(LocalDateSerializer::class)

package no.nav.hag.simba.utils.felles.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class Ansettelsesforhold(
    val startdato: LocalDate,
    val sluttdato: LocalDate?,
    val yrkeskode: String?,
    val yrkesbeskrivelse: String?,
    val stillingsprosent: Double?,
)
