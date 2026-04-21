package no.nav.helsearbeidsgiver.inntektsmelding.api.hentarbeidsforholdselvbestemt

import kotlinx.serialization.Serializable
import no.nav.hag.simba.utils.felles.domene.PeriodeAapen

@Serializable
data class HentArbeidsforholdSelvbestemtResponse(
    val ansettelsesperioder: Set<PeriodeAapen>,
)
