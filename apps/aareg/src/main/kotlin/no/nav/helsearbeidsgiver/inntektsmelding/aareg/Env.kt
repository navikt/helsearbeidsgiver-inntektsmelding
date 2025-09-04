package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import no.nav.hag.simba.utils.felles.utils.fromEnv

object Env {
    val aaregScope = "AAREG_SCOPE".fromEnv()
    val aaregUrl = "AAREG_URL".fromEnv()
}
