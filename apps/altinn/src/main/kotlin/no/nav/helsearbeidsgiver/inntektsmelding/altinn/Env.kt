package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import no.nav.hag.simba.utils.felles.utils.fromEnv

object Env {
    val altinnScope = "ALTINN_TILGANGER_SCOPE".fromEnv()
    val serviceCode = "ALTINN_SERVICE_CODE".fromEnv()
    val altinnTilgangerBaseUrl = "ALTINN_TILGANGER_BASE_URL".fromEnv()
}
