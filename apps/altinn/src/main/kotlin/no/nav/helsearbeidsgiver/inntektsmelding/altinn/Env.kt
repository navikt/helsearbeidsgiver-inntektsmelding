package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import no.nav.helsearbeidsgiver.felles.fromEnv

object Env {
    val url = "ALTINN_URL".fromEnv()
    val serviceCode = "ALTINN_SERVICE_CODE".fromEnv()
    val apiGwApiKey = "ALTINN_API_GW_API_KEY".fromEnv()
    val altinnApiKey = "ALTINN_API_KEY".fromEnv()
}
