package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import no.nav.helsearbeidsgiver.felles.ArbeidsforholdLøsning
import no.nav.helsearbeidsgiver.felles.InntektLøsning
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.SykLøsning
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning

data class PreutfyltResultat(
    val FULLT_NAVN: NavnLøsning?,
    val VIRKSOMHET: VirksomhetLøsning?,
    val ARBEIDSFORHOLD: ArbeidsforholdLøsning?,
    val SYK: SykLøsning?,
    val INNTEKT: InntektLøsning?
)
