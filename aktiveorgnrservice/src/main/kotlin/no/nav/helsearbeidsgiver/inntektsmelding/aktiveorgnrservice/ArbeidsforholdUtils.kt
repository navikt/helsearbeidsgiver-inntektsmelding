package no.nav.helsearbeidsgiver.inntektsmelding.aktiveorgnrservice

import no.nav.helsearbeidsgiver.felles.Arbeidsforhold

fun List<Arbeidsforhold>.filterOrgnr(vararg organisasjoner: String): List<Arbeidsforhold> {
    return this
        .filter { it.arbeidsgiver.organisasjonsnummer in organisasjoner }
}

fun List<Arbeidsforhold>.orgnrMedHistoriskArbeidsforhold(): List<String> {
    return this
        .mapNotNull { it.arbeidsgiver.organisasjonsnummer }
        .distinct()
}
