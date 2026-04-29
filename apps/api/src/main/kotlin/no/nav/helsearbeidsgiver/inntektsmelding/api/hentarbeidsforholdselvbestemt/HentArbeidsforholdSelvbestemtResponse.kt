package no.nav.helsearbeidsgiver.inntektsmelding.api.hentarbeidsforholdselvbestemt

import kotlinx.serialization.Serializable
import no.nav.hag.simba.utils.felles.domene.Ansettelsesforhold

@Serializable
data class HentArbeidsforholdSelvbestemtResponse(
    val ansettelsesforhold: Set<Ansettelsesforhold>,
)
