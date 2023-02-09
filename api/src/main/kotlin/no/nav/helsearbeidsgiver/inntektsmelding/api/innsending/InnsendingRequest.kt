@file:UseSerializers(LocalDateSerializer::class)
@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonClassDiscriminator
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class InnsendingRequest(
    val orgnrUnderenhet: String,
    val identitetsnummer: String,
    val behandlingsdager: List<LocalDate>,
    val egenmeldingsperioder: List<Periode>,
    val arbeidsgiverperioder: List<Periode>,
    val bestemmendeFraværsdag: LocalDate,
    val fraværsperioder: List<Periode>,
    val inntekt: Inntekt,
    val fullLønnIArbeidsgiverPerioden: FullLønnIArbeidsgiverPerioden,
    val refusjon: Refusjon,
    val naturalytelser: List<Naturalytelse>? = null,
    val årsakInnsending: ÅrsakInnsending,
    val bekreftOpplysninger: Boolean
)

@Serializable
enum class BegrunnelseIngenEllerRedusertUtbetalingKode {
    LovligFravaer,
    FravaerUtenGyldigGrunn,
    ArbeidOpphoert,
    BeskjedGittForSent,
    ManglerOpptjening,
    IkkeLoenn,
    BetvilerArbeidsufoerhet,
    IkkeFravaer,
    StreikEllerLockout,
    Permittering,
    FiskerMedHyre,
    Saerregler,
    FerieEllerAvspasering,
    IkkeFullStillingsandel,
    TidligereVirksomhet
}

@Serializable
data class FullLønnIArbeidsgiverPerioden(
    val utbetalerFullLønn: Boolean,
    val begrunnelse: BegrunnelseIngenEllerRedusertUtbetalingKode? = null,
    val utbetalt: Double? = null
)

@Serializable
data class Inntekt(
    var bekreftet: Boolean,
    var beregnetInntekt: Double,
    val endringÅrsak: InntektEndringÅrsak? = null,
    val manueltKorrigert: Boolean
)

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

@Serializable
data class Naturalytelse(
    val naturalytelse: NaturalytelseKode,
    val dato: LocalDate,
    val beløp: Double
)

/**
 * https://github.com/navikt/tjenestespesifikasjoner/blob/IM_nye_kodeverdier/nav-altinn-inntektsmelding/src/main/xsd/Inntektsmelding_kodelister_20210216.xsd
 */
/* ktlint-disable enum-entry-name-case */
/* ktlint-disable EnumEntryName */
@Suppress("EnumEntryName", "unused")
enum class NaturalytelseKode {
    AksjerGrunnfondsbevisTilUnderkurs,
    Losji,
    KostDoegn,
    BesoeksreiserHjemmetAnnet,
    KostbesparelseIHjemmet,
    RentefordelLaan,
    Bil,
    KostDager,
    Bolig,
    SkattepliktigDelForsikringer,
    FriTransport,
    Opsjoner,
    TilskuddBarnehageplass,
    Annet,
    Bedriftsbarnehageplass,
    YrkebilTjenestligbehovKilometer,
    YrkebilTjenestligbehovListepris,
    InnbetalingTilUtenlandskPensjonsordning,
    ElektroniskKommunikasjon
}

@Serializable
data class Refusjon(
    val utbetalerHeleEllerDeler: Boolean,
    val refusjonPrMnd: Double? = null,
    val refusjonOpphører: LocalDate? = null,
    val refusjonEndringer: List<RefusjonEndring>? = null
)

@Serializable
data class RefusjonEndring(
    val beløp: Double,
    val dato: LocalDate
)

@Serializable
enum class ÅrsakInnsending {
    Ny,
    Endring
}
