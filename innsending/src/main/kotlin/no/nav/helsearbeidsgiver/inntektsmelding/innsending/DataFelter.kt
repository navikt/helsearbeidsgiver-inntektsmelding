package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Key

enum class DataFelter(val str: String) {

    VIRKSOMHET(DataFelt.VIRKSOMHET.str),
    ARBEIDSFORHOLD(DataFelt.ARBEIDSFORHOLD.str),
    INNTEKTSMELDING_DOKUMENT(Key.INNTEKTSMELDING_DOKUMENT.str),
    ARBEIDSTAKER_INFORMASJON(DataFelt.ARBEIDSTAKER_INFORMASJON.str)
}




