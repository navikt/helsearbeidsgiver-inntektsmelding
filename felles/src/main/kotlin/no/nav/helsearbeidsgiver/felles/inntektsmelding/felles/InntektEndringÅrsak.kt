@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.felles.inntektsmelding.felles

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonClassDiscriminator
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import java.time.LocalDate

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
sealed class InntektEndringÅrsak {
    @Serializable
    @SerialName("Tariffendring")
    data class Tariffendring(val gjelderFra: LocalDate, val bleKjent: LocalDate) : InntektEndringÅrsak()

    @Serializable
    @SerialName("Ferie")
    data class Ferie(val liste: List<Periode>) : InntektEndringÅrsak()

    @Serializable
    @SerialName("VarigLønnsendring")
    data class VarigLønnsendring(val gjelderFra: LocalDate) : InntektEndringÅrsak()

    @Serializable
    @SerialName("Permisjon")
    data class Permisjon(val liste: List<Periode>) : InntektEndringÅrsak()

    @Serializable
    @SerialName("Permittering")
    data class Permittering(val liste: List<Periode>) : InntektEndringÅrsak()

    @Serializable
    @SerialName("NyStilling")
    data class NyStilling(val gjelderFra: LocalDate) : InntektEndringÅrsak()

    @Serializable
    @SerialName("NyStillingsprosent")
    data class NyStillingsprosent(val gjelderFra: LocalDate) : InntektEndringÅrsak()

    @Serializable
    @SerialName("Bonus")
    object Bonus : InntektEndringÅrsak()
}
