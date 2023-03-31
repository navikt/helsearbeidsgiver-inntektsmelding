package no.nav.helsearbeidsgiver.inntektsmelding.api

import no.nav.helsearbeidsgiver.felles.fromEnv

object Env {
    object Auth {
        val discoveryUrl: String = "LOGINSERVICE_IDPORTEN_DISCOVERY_URL".fromEnv()
        val acceptedAudience: List<String> = "LOGINSERVICE_IDPORTEN_AUDIENCE".fromEnv().let(::listOf)
    }
    object Altinn {
        val url = "ALTINN_URL".fromEnv()
        val serviceCode = "ALTINN_SERVICE_CODE".fromEnv()
        val apiGwApiKey = "ALTINN_API_GW_API_KEY".fromEnv()
        val altinnApiKey = "ALTINN_API_KEY".fromEnv()
    }
    object Redis {
        val url: String = "REDIS_URL".fromEnv()
    }
}
