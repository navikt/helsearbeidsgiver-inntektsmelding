@file:Suppress("DEPRECATION")

package no.nav.helsearbeidsgiver.inntektsmelding.db.domene

import kotlinx.serialization.Serializable

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
data class FullLoennIAgpGammeltFormat(
    val utbetalerFullLønn: Boolean,
    val begrunnelse: BegrunnelseIngenEllerRedusertUtbetalingKodeGammeltFormat? = null,
    val utbetalt: Double? = null,
)

/** Bruker UpperCamelCase for å matche kodeverkverdier. */
@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
enum class BegrunnelseIngenEllerRedusertUtbetalingKodeGammeltFormat {
    ArbeidOpphoert,
    BeskjedGittForSent,
    BetvilerArbeidsufoerhet,
    FerieEllerAvspasering,
    FiskerMedHyre,
    FravaerUtenGyldigGrunn,
    IkkeFravaer,
    IkkeFullStillingsandel,
    IkkeLoenn,
    LovligFravaer,
    ManglerOpptjening,
    Permittering,
    Saerregler,
    StreikEllerLockout,
    TidligereVirksomhet,
}
