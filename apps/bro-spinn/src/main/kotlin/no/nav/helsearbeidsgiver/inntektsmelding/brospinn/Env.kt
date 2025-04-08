package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import no.nav.helsearbeidsgiver.felles.utils.fromEnv

object Env {
    val spinnScope = "SPINN_SCOPE".fromEnv()
    val spinnUrl = "SPINN_API_URL".fromEnv()
}
