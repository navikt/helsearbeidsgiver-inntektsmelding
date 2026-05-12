package no.nav.helsearbeidsgiver.inntektsmelding.api.hentarbeidsforhold

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.Ansettelsesforhold

@Serializable
data class HentArbeidsforholdResponse(
    val ansettelsesforhold: List<Ansettelsesforhold>,
)
