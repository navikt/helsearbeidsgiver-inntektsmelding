@file:UseSerializers(LocalDateSerializer::class, YearMonthSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonClassDiscriminator
import no.nav.helsearbeidsgiver.felles.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.felles.json.serializer.YearMonthSerializer
import java.time.LocalDate
import java.time.YearMonth

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("opplysningstype")
sealed class ForespurtData {
    @Serializable
    @SerialName("Arbeidsgiverperiode")
    object ArbeidsgiverPeriode : ForespurtData()

    @Serializable
    @SerialName("Inntekt")
    data class Inntekt(val forslag: ForslagInntekt) : ForespurtData()

    @Serializable
    @SerialName("FastsattInntekt")
    data class FastsattInntekt(val fastsattInntekt: Double) : ForespurtData()

    @Serializable
    @SerialName("Refusjon")
    data class Refusjon(val forslag: List<ForslagRefusjon>) : ForespurtData()
}

@Serializable
data class ForslagRefusjon(
    val fom: LocalDate,
    val tom: LocalDate?,
    val beløp: Double
)

@Serializable
data class ForslagInntekt(
    val beregningsmåneder: List<YearMonth>
)
