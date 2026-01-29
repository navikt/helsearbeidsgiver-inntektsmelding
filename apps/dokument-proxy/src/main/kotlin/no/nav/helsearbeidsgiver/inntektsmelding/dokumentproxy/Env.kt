package no.nav.helsearbeidsgiver.inntektsmelding.dokumentproxy

import no.nav.hag.simba.utils.felles.utils.fromEnv

object Env {
    val lpsApiTarget = "LPS_API_TARGET".fromEnv()
    val lpsApiBaseurl = "LPS_API_BASEURL".fromEnv()
}
