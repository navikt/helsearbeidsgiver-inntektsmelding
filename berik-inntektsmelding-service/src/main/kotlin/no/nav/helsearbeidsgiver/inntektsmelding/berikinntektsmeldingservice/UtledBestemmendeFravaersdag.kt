package no.nav.helsearbeidsgiver.inntektsmelding.berikinntektsmeldingservice

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.bestemmendeFravaersdag
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import java.time.LocalDate

/**
 * Vi bruker Spleis sitt forslag til bestemmende fraværsdag (BF), med to unntak der vi må beregne BF selv:
 * - Vi ber om AGP. Da kan AGP inneholde info som Spleis ikke vet om, f. eks. egenmeldinger.
 * - Vi ber kun om refusjon. Da kan Spleis sitt forslag inneholde feil`*` dersom sykmeldt har mer enn én arbeidsgiver.
 *
 * `*` Det er ikke feil, men forslagene fra Spleis er egentlig inntektsdatoer, ikke BF-er.
 * For én arbeidsgiver så er disse datoene like, men det er de nødvendigvis ikke ved mer enn én arbeidsgiver.
*/
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
            sykefravaersperioder = forespoersel.sykmeldingsperioder,
        )
    } else {
        forespoersel.forslagBestemmendeFravaersdag()
    }
