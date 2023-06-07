package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helsearbeidsgiver.felles.DataFelt
// @TODO vi trenger ikke dette
enum class DataFelter(val str: String) {

    VIRKSOMHET(DataFelt.VIRKSOMHET.str),
    ARBEIDSFORHOLD(DataFelt.ARBEIDSFORHOLD.str),
    INNTEKTSMELDING_DOKUMENT(DataFelt.INNTEKTSMELDING_DOKUMENT.str),
    ARBEIDSTAKER_INFORMASJON(DataFelt.ARBEIDSTAKER_INFORMASJON.str)
}
