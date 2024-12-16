package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel

// Så lenge frontend sender felter som ikke kreves av Spleis så må vi filtrere ut disse for å sammenligne skjema
fun SkjemaInntektsmelding.erDuplikatAv(
    other: SkjemaInntektsmelding,
    forespoersel: Forespoersel,
): Boolean =
    this ==
        other.copy(
            avsenderTlf = avsenderTlf,
            agp =
                if (forespoersel.forespurtData.arbeidsgiverperiode.paakrevd) {
                    other.agp
                } else {
                    agp
                },
            inntekt =
                if (forespoersel.forespurtData.inntekt.paakrevd) {
                    other.inntekt
                } else {
                    inntekt
                },
            refusjon =
                if (forespoersel.forespurtData.refusjon.paakrevd) {
                    other.refusjon
                } else {
                    refusjon
                },
        )
