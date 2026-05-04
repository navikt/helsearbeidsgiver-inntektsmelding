package no.nav.helsearbeidsgiver.inntektsmelding.api.hentarbeidsforhold

import kotlinx.serialization.Serializable
import no.nav.hag.simba.utils.felles.domene.Ansettelsesforhold

@Serializable
data class HentArbeidsforholdResponse(
    val ansettelsesforhold: Set<Ansettelsesforhold>,
)
