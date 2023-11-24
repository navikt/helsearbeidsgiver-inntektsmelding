package no.nav.helsearbeidsgiver.inntektsmelding.aktiveorgnrservice

import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import java.time.LocalDate

fun List<Arbeidsforhold>.medOrgnr(vararg organisasjoner: String): List<Arbeidsforhold> {
    return this
        .filter { it.arbeidsgiver.organisasjonsnummer in organisasjoner }
}

fun List<Arbeidsforhold>.orgnrMedAktivtArbeidsforhold(dato: LocalDate = LocalDate.now()): List<String> {
    return this
        .filter { it.ansettelsesperiode.periode.tom?.isAfter(dato) ?: true }
        .mapNotNull { it.arbeidsgiver.organisasjonsnummer }
        .distinct()
}
