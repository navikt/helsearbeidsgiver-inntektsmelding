@file:UseSerializers(LocalDateSerializer::class, YearMonthSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonClassDiscriminator
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import no.nav.helsearbeidsgiver.felles.serializers.YearMonthSerializer
import java.time.YearMonth

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("opplysningstype") // TODO fungerer ikke med Json.encodeToJsonElement pga. feil i 1.5.0-RC
sealed class ForespurtData {
    @Serializable
    @SerialName("Arbeidsgiverperiode")
    data class ArbeidsgiverPeriode(val forslag: List<Periode>) : ForespurtData()

    @Serializable
    @SerialName("Inntekt")
    data class Inntekt(val forslag: ForslagInntekt) : ForespurtData()

    @Serializable
    @SerialName("FastsattInntekt")
    data class FastsattInntekt(val fastsattInntekt: Double) : ForespurtData()

    @Serializable
    @SerialName("Refusjon")
    object Refusjon : ForespurtData()
}

@Serializable
data class ForslagInntekt(
    val beregningsmåneder: List<YearMonth>
)
