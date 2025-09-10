package no.nav.helsearbeidsgiver.inntektsmelding.innsending.api

import no.nav.hag.simba.utils.felles.utils.gjennomsnitt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import java.time.YearMonth

const val FEILMARGIN_INNTEKT_A_ORDNING: Double = 10.0

/**
 * Validerer om inntekten i inntektsmeldingen avviker fra inntekt i A-ordningen uten at det er oppgitt endringsårsak
 */
fun SkjemaInntektsmelding.validerInntektMotAordningen(aordningInntekt: Map<YearMonth, Double?>): Set<Feilkode> {
    val inntektsmeldingInntekt = this.inntekt

    // Hvis inntektsmelding mangler inntekt eller har oppgitt endringsårsak, så er det ikke behov for validering mot A-ordningen
    if (inntektsmeldingInntekt == null || inntektsmeldingInntekt.endringAarsaker.isNotEmpty()) {
        return emptySet()
    }

    val aordningSnittInntekt = aordningInntekt.gjennomsnitt()

    val inntektErUtenforFeilmargin =
        inntektsmeldingInntekt.beloep !in
            ((aordningSnittInntekt - FEILMARGIN_INNTEKT_A_ORDNING)..(aordningSnittInntekt + FEILMARGIN_INNTEKT_A_ORDNING))

    return when {
        inntektErUtenforFeilmargin || aordningInntekt.isEmpty() ->
            setOf(Feilkode.INNTEKT_A_ORDNINGEN_AVVIK_MANGLER_AARSAK)

        else -> emptySet()
    }
}
