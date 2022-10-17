package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

@Serializable
data class Resultat(
    val FULLT_NAVN: NavnLøsning? = null,
    val VIRKSOMHET: VirksomhetLøsning? = null,
    val ARBEIDSFORHOLD: ArbeidsforholdLøsning? = null,
    val SYK: SykLøsning? = null,
    val INNTEKT: InntektLøsning? = null,
    val EGENMELDING: EgenmeldingLøsning? = null
)
