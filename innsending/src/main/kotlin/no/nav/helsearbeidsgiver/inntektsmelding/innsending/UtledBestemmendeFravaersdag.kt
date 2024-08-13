package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.bestemmendeFravaersdag
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import java.time.LocalDate

fun utledBestemmendeFravaersdag(
    forespoersel: Forespoersel,
    inntektsmelding: Inntektsmelding,
): LocalDate =
    if (
        forespoersel.forespurtData.arbeidsgiverperiode.paakrevd ||
        (!forespoersel.forespurtData.inntekt.paakrevd && forespoersel.forespurtData.refusjon.paakrevd)
    ) {
        bestemmendeFravaersdag(
            arbeidsgiverperioder = inntektsmelding.agp?.perioder.orEmpty(),
            sykmeldingsperioder = forespoersel.sykmeldingsperioder,
        )
    } else {
        forespoersel.forslagBestemmendeFravaersdag()
    }
