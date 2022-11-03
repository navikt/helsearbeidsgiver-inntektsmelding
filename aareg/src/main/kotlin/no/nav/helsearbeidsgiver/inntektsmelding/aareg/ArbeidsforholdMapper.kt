package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import no.nav.helsearbeidsgiver.aareg.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.Ansettelsesperiode
import no.nav.helsearbeidsgiver.felles.Arbeidsgiver
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold as ArbeidsforholdResultat

fun Arbeidsforhold.tilArbeidsforhold(): ArbeidsforholdResultat =
    ArbeidsforholdResultat(
        Arbeidsgiver(
            type = arbeidsgiver.type,
            organisasjonsnummer = arbeidsgiver.organisasjonsnummer
        ),
        Ansettelsesperiode(
            Periode(
                ansettelsesperiode.periode.fom,
                ansettelsesperiode.periode.tom
            )
        ),
        registrert
    )
