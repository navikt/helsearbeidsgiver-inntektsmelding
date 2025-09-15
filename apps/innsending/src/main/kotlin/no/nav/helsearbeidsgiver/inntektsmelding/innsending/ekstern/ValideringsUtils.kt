package no.nav.helsearbeidsgiver.inntektsmelding.innsending.ekstern

import no.nav.hag.simba.utils.felles.utils.gjennomsnitt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import java.time.YearMonth
import kotlin.math.abs

const val FEILMARGIN_INNTEKT_A_ORDNING_KRONER: Double = 10.0

/**
 * Validerer om inntekten i inntektsmeldingen avviker fra inntekten i a-ordningen
 */
fun Inntekt.validerInntektMotAordningen(aordningInntekt: Map<YearMonth, Double?>): Set<Feilkode> {
    val aordningSnittInntekt = aordningInntekt.gjennomsnitt()

    val inntektErUtenforFeilmargin = abs(beloep - aordningSnittInntekt) > FEILMARGIN_INNTEKT_A_ORDNING_KRONER

    return if (inntektErUtenforFeilmargin || aordningInntekt.all { it.value == null }) {
        setOf(Feilkode.INNTEKT_AVVIKER_FRA_A_ORDNINGEN)
    } else {
        emptySet()
    }
}
