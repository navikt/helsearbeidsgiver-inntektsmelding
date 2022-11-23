@file:UseSerializers(LocalDateSerializer::class)
@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class InntektsmeldingDokument(
    val orgnrUnderenhet: String,
    val identitetsnummer: String,
    var fulltNavn: String,
    var virksomhetNavn: String,
    val behandlingsdager: List<LocalDate>,
    val egenmeldingsperioder: List<EgenmeldingPeriode>,
    val bruttoInntekt: Bruttoinntekt,
    // Betaljer arbeidsgiver full lønn til arbeidstaker
    val fullLønnIArbeidsgiverPerioden: FullLønnIArbeidsgiverPerioden,
    // Betaler arbeidsgiver lønn under hele eller deler av sykefraværet
    val heleEllerdeler: HeleEllerdeler,
    // Naturalytelser
    val naturalytelser: List<Naturalytelse>? = null,
    val bekreftOpplysninger: Boolean
)

@Serializable
data class EgenmeldingPeriode(
    val fom: LocalDate,
    val tom: LocalDate
)

@Serializable
data class Naturalytelse(
    val naturalytelseKode: String,
    val dato: LocalDate,
    val beløp: Double
)

@Serializable
data class Bruttoinntekt(
    var bekreftet: Boolean,
    var bruttoInntekt: Double,
    val endringaarsak: String? = null, // TODO Lage enum?
    val manueltKorrigert: Boolean
)

@Serializable
data class FullLønnIArbeidsgiverPerioden(
    val utbetalerFullLønn: Boolean,
    val begrunnelse: BegrunnelseIngenEllerRedusertUtbetalingKode? = null
)

@Serializable
data class HeleEllerdeler(
    val utbetalerHeleEllerDeler: Boolean,
    val refusjonPrMnd: Double? = null,
    val opphørSisteDag: LocalDate? = null
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
