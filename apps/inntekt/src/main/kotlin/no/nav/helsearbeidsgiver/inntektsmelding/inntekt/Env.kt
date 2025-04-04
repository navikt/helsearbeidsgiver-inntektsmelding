package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import no.nav.helsearbeidsgiver.felles.utils.fromEnv

object Env {
    val inntektScope = "INNTEKT_SCOPE".fromEnv()
    val inntektUrl = "INNTEKT_URL".fromEnv()
}
