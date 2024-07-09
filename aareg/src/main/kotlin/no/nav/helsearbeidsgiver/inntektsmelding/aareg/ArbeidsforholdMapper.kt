package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import no.nav.helsearbeidsgiver.felles.Ansettelsesperiode
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.Arbeidsgiver
import no.nav.helsearbeidsgiver.felles.PeriodeNullable
import no.nav.helsearbeidsgiver.aareg.Arbeidsforhold as KlientArbeidsforhold

fun KlientArbeidsforhold.tilArbeidsforhold(): Arbeidsforhold =
    Arbeidsforhold(
        arbeidsgiver =
            Arbeidsgiver(
                type = arbeidsgiver.type,
                organisasjonsnummer = arbeidsgiver.organisasjonsnummer,
            ),
        ansettelsesperiode =
            Ansettelsesperiode(
                PeriodeNullable(
                    ansettelsesperiode.periode.fom,
                    ansettelsesperiode.periode.tom,
                ),
            ),
        registrert = registrert,
    )
