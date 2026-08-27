package no.nav.helsearbeidsgiver.inntektsmelding.soeknad

import no.nav.hag.simba.utils.felles.utils.fromEnv

object Env {
    val soeknadBaseUrl = "SOEKNAD_BASE_URL".fromEnv()
    val soeknadScope = "SOEKNAD_SCOPE".fromEnv()
}
