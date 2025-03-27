@file:Suppress("DEPRECATION")
@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.db.domene

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonClassDiscriminator
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.time.LocalDate

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
data class InntektGammeltFormat(
    val bekreftet: Boolean,
    val beregnetInntekt: Double,
    val endringÅrsak: InntektEndringAarsakGammeltFormat? = null,
    val manueltKorrigert: Boolean,
)

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("typpe")
sealed class InntektEndringAarsakGammeltFormat

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
@SerialName("Bonus")
data class BonusGammeltFormat(
    val aarligBonus: Double? = null,
    val datoForBonus: LocalDate? = null,
) : InntektEndringAarsakGammeltFormat()

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
@SerialName("Feilregistrert")
data object FeilregistrertGammeltFormat : InntektEndringAarsakGammeltFormat()

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
@SerialName("Ferie")
data class FerieGammeltFormat(
    val liste: List<Periode>,
) : InntektEndringAarsakGammeltFormat()

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
@SerialName("Ferietrekk")
data object FerietrekkGammeltFormat : InntektEndringAarsakGammeltFormat()

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
@SerialName("Nyansatt")
data object NyansattGammeltFormat : InntektEndringAarsakGammeltFormat()

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
@SerialName("NyStilling")
data class NyStillingGammeltFormat(
    val gjelderFra: LocalDate,
) : InntektEndringAarsakGammeltFormat()

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
@SerialName("NyStillingsprosent")
data class NyStillingsprosentGammeltFormat(
    val gjelderFra: LocalDate,
) : InntektEndringAarsakGammeltFormat()

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
@SerialName("Permisjon")
data class PermisjonGammeltFormat(
    val liste: List<Periode>,
) : InntektEndringAarsakGammeltFormat()

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
@SerialName("Permittering")
data class PermitteringGammeltFormat(
    val liste: List<Periode>,
) : InntektEndringAarsakGammeltFormat()

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
@SerialName("Sykefravaer")
data class SykefravaerGammeltFormat(
    val liste: List<Periode>,
) : InntektEndringAarsakGammeltFormat()

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
@SerialName("Tariffendring")
data class TariffendringGammeltFormat(
    val gjelderFra: LocalDate,
    val bleKjent: LocalDate,
) : InntektEndringAarsakGammeltFormat()

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
@SerialName("VarigLonnsendring")
data class VarigLonnsendringGammeltFormat(
    val gjelderFra: LocalDate,
) : InntektEndringAarsakGammeltFormat()
