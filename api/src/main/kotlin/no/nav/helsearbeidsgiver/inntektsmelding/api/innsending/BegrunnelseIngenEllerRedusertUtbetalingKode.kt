@file:Suppress("unused")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import kotlinx.serialization.Serializable

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
