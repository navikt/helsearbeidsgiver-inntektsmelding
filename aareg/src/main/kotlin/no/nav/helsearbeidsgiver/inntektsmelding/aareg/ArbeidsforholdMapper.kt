package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import no.nav.helsearbeidsgiver.felles.domene.Ansettelsesperiode
import no.nav.helsearbeidsgiver.felles.domene.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.domene.Arbeidsgiver
import no.nav.helsearbeidsgiver.felles.domene.PeriodeNullable
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
