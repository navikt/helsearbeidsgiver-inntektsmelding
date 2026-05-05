package no.nav.helsearbeidsgiver.inntektsmelding.api.hentarbeidsforholdselvbestemt

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.inntektsmelding.api.hentarbeidsforhold.AnsettelsesforholdResponse

@Serializable
data class HentArbeidsforholdSelvbestemtResponse(
    val ansettelsesforhold: Set<AnsettelsesforholdResponse>,
)
