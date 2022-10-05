package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import no.nav.helsearbeidsgiver.felles.Løsning

data class PreutfyltResultat(
    val FULLT_NAVN: Løsning?,
    val VIRKSOMHET: Løsning?,
    val ARBEIDSFORHOLD: Løsning?,
    val SYK: Løsning?,
    val INNTEKT: Løsning?
)
