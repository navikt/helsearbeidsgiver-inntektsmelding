package no.nav.helsearbeidsgiver.inntektsmelding.api

import no.nav.helsearbeidsgiver.felles.fromEnv

object Env {
    object Auth {
        val discoveryUrl: String = "LOGINSERVICE_IDPORTEN_DISCOVERY_URL".fromEnv()
        val acceptedAudience: List<String> = "LOGINSERVICE_IDPORTEN_AUDIENCE".fromEnv().let(::listOf)
    }

    object Redis {
        val url: String = "REDIS_URL".fromEnv()
    }
}
