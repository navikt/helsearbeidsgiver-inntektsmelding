@file:UseSerializers(LocalDateSerializer::class, LocalDateTimeSerializer::class)
@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateTimeSerializer
import java.time.LocalDate
import java.time.LocalDateTime

@Serializable
data class InntektsmeldingDokument(
    val orgnrUnderenhet: String,
    val identitetsnummer: String,
    var fulltNavn: String,
    var virksomhetNavn: String,
    val behandlingsdager: List<LocalDate>,
    val egenmeldingsperioder: List<Periode>,
    val bestemmendeFraværsdag: LocalDate,
    val fraværsperioder: List<Periode>,
    val arbeidsgiverperioder: List<Periode>,
    var beregnetInntekt: Double,
    val beregnetInntektEndringÅrsak: ÅrsakBeregnetInntektEndringKodeliste? = null,
    val fullLønnIArbeidsgiverPerioden: FullLønnIArbeidsgiverPerioden,
    val refusjon: Refusjon,
    val naturalytelser: List<Naturalytelse>? = null,
    val tidspunkt: LocalDateTime,
    val årsakInnsending: ÅrsakInnsending,
    val identitetsnummerInnsender: String
)

@Serializable
data class Periode(
    val fom: LocalDate,
    val tom: LocalDate
)

@Serializable
data class Naturalytelse(
    val naturalytelse: NaturalytelseKode,
    val dato: LocalDate,
    val beløp: Double
)

@Serializable
data class FullLønnIArbeidsgiverPerioden(
    val utbetalerFullLønn: Boolean,
    val begrunnelse: BegrunnelseIngenEllerRedusertUtbetalingKode? = null,
    val utbetalt: Double? = null
)

@Serializable
data class Refusjon(
    val refusjonPrMnd: Double? = null,
    val refusjonOpphører: LocalDate? = null
)

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
enum class ÅrsakInnsending {
    Ny,
    Endring
}

@Serializable
enum class ÅrsakBeregnetInntektEndringKodeliste {
    Tariffendring,
    FeilInntekt
}

@Serializable
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
