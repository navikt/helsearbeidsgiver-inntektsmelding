package no.nav.helsearbeidsgiver.inntektsmelding.api

import no.nav.helsearbeidsgiver.felles.utils.fromEnv

object Env {
    object Auth {
        val discoveryUrl = "IDPORTEN_WELL_KNOWN_URL".fromEnv()
        val acceptedAudience = "IDPORTEN_AUDIENCE".fromEnv().let(::listOf)
    }

    object Redis {
        val host = "REDIS_HOST_INNTEKTSMELDING".fromEnv()
        val port = "REDIS_PORT_INNTEKTSMELDING".fromEnv().toInt()
        val username = "REDIS_USERNAME_INNTEKTSMELDING".fromEnv()
        val password = "REDIS_PASSWORD_INNTEKTSMELDING".fromEnv()

        val url = "REDIS_URL".fromEnv()
    }
}
