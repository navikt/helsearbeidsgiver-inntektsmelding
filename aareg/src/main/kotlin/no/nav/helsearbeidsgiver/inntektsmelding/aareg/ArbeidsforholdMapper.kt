package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import no.nav.helsearbeidsgiver.aareg.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.Ansettelsesperiode
import no.nav.helsearbeidsgiver.felles.Arbeidsgiver
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold as ArbeidsforholdResultat

fun arbeidsforholdMapper(arbeidsforhold: List<Arbeidsforhold>): List<ArbeidsforholdResultat> = arbeidsforhold.map {
    ArbeidsforholdResultat(
        Arbeidsgiver(
            type = it.arbeidsgiver.type,
            organisasjonsnummer = it.arbeidsgiver.organisasjonsnummer
        ),
        ansettelsesperiode = Ansettelsesperiode(
            Periode(
                it.ansettelsesperiode.periode.fom,
                it.ansettelsesperiode.periode.tom
            )
        ),
        registrert = it.registrert
    )
}
