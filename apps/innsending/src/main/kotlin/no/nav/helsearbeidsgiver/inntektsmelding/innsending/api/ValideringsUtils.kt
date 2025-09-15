package no.nav.helsearbeidsgiver.inntektsmelding.innsending.api

import no.nav.hag.simba.utils.felles.utils.gjennomsnitt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import java.time.YearMonth
import kotlin.math.abs

const val FEILMARGIN_INNTEKT_A_ORDNING_KRONER: Double = 10.0

/**
 * Validerer om inntekten i inntektsmeldingen avviker fra inntekt i A-ordningen uten at det er oppgitt endringsårsak
 */
fun Inntekt.validerInntektMotAordningen(aordningInntekt: Map<YearMonth, Double?>): Set<Feilkode> {
    val aordningSnittInntekt = aordningInntekt.gjennomsnitt()

    val inntektErUtenforFeilmargin = abs(beloep - aordningSnittInntekt) >= FEILMARGIN_INNTEKT_A_ORDNING_KRONER

    return if (inntektErUtenforFeilmargin || aordningInntekt.isEmpty()) {
        setOf(Feilkode.INNTEKT_AVVIKER_FRA_A_ORDNINGEN)
    } else {
        emptySet()
    }
}
