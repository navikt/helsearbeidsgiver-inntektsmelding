@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonClassDiscriminator
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import java.time.LocalDate

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("opplysningstype")
sealed class ForespurtData

@Serializable
@SerialName("Arbeidsgiverperiode")
data class ArbeidsgiverPeriode(val forslag: List<Forslag>) : ForespurtData()

@Serializable
@SerialName("Refusjon")
object Refusjon : ForespurtData()

@Serializable
@SerialName("Inntekt")
object Inntekt : ForespurtData()

@Serializable
data class Forslag(
    val fom: LocalDate,
    val tom: LocalDate
)
