package no.nav.helsearbeidsgiver.inntektsmelding.api.hentarbeidsforhold

import kotlinx.serialization.Serializable
import no.nav.hag.simba.utils.felles.domene.PeriodeAapen

@Serializable
data class HentArbeidsforholdResponse(
    val ansettelsesperioder: List<PeriodeAapen>,
)
