package no.nav.helsearbeidsgiver.inntektsmelding.innsending.ekstern

import no.nav.hag.simba.utils.felles.utils.gjennomsnitt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.time.YearMonth
import kotlin.math.abs

private const val FEILMARGIN_INNTEKT_A_ORDNING_KRONER: Double = 10.0

/** Validerer om inntekten i inntektsmeldingen avviker fra inntekten i a-ordningen. */
fun Inntekt.validerInntektMotAordningen(aordningInntekt: Map<YearMonth, Double?>): Set<Feil> {
    val aordningSnittInntekt = aordningInntekt.gjennomsnitt()

    val inntektErUtenforFeilmargin = abs(beloep - aordningSnittInntekt) > FEILMARGIN_INNTEKT_A_ORDNING_KRONER

    return if (inntektErUtenforFeilmargin || aordningInntekt.all { it.value == null }) {
        setOf(Feil(Feilkode.INNTEKT_AVVIKER_FRA_A_ORDNINGEN, "Oppgitt beløp $beloep matcher ikke snittinntekt i A-ordning: $aordningSnittInntekt")).also {
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
