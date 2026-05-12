package no.nav.helsearbeidsgiver.inntektsmelding.api.hentarbeidsforholdselvbestemt

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.Ansettelsesforhold

@Serializable
data class HentArbeidsforholdSelvbestemtResponse(
    val ansettelsesforhold: List<Ansettelsesforhold>,
)
