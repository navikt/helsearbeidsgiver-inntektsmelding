package no.nav.helsearbeidsgiver.inntektsmelding.dokumentproxy

import no.nav.hag.simba.utils.felles.utils.fromEnv

object Env {
    val lpsApiScope = "LPS_API_SCOPE".fromEnv()
    val lpsApiBaseurl = "LPS_API_BASEURL".fromEnv()
}
