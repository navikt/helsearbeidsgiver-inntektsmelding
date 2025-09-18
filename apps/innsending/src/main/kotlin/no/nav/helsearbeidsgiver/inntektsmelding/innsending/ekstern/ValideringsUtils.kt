package no.nav.helsearbeidsgiver.inntektsmelding.innsending.ekstern

import no.nav.hag.simba.utils.felles.utils.gjennomsnitt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
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
        setOf(Feilkode.INNTEKT_AVVIKER_FRA_A_ORDNINGEN).also {
            sikkerLogger().info(
                "Validering av inntekt mot a-ordningen resulterte i feilen INNTEKT_AVVIKER_FRA_A_ORDNINGEN. Inntekt i inntektsmelding: $beloep kroner, " +
                    "utregnet gjennomsnitt fra a-ordninginntekter: $aordningSnittInntekt kroner, " +
                    "feilmargin for validering: $FEILMARGIN_INNTEKT_A_ORDNING_KRONER kroner, " +
                    "inntekter hentet fra a-ordningen: $aordningInntekt.",
            )
        }
    } else {
        emptySet()
    }
}
