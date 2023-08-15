package no.nav.helsearbeidsgiver.inntektsmelding.trengerservice

import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.time.LocalDate

/**
 * Skjæringstidspunkt = fom fra nyligste perioden.
 * Perioder som ikke har opphold over hverdager anses som sammenhengende.
 */
fun finnSkjaeringstidspunkt(sykmeldingsperioder: List<Periode>): LocalDate? =
    sykmeldingsperioder.sortedBy { it.fom }
        .reduceOrNull { p1, p2 ->
            p1.slåSammenIgnorerHelgOrNull(p2)
                .orDefault(p2)
        }
        ?.fom
