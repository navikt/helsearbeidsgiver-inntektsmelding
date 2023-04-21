package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Key

enum class DataFelter(val str: String) {
    VIRKSOMHET(DataFelt.VIRKSOMHET.str),
    ARBEIDSFORHOLD(DataFelter.ARBEIDSFORHOLD.str),
    INNTEKTSMELDING_REQUEST(Key.INNTEKTSMELDING.str),
    INNTEKTSMELDING_DOKUMENT(Key.INNTEKTSMELDING_DOKUMENT.str),
    INITIATE_ID(Key.INITIATE_ID.str),
    ARBEIDSTAKER_INFORMASJON(DataFelt.ARBEIDSTAKER_INFORMASJON.str)
}
