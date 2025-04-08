package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import no.nav.helsearbeidsgiver.felles.utils.fromEnv

object Env {
    val aaregScope = "AAREG_SCOPE".fromEnv()
    val aaregUrl = "AAREG_URL".fromEnv()
}
