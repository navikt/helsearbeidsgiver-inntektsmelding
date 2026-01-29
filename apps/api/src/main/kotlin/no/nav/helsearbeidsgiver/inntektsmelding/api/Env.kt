package no.nav.helsearbeidsgiver.inntektsmelding.api

import no.nav.hag.simba.utils.felles.utils.fromEnv

object Env {
    val kafkaTopic = "KAFKA_RAPID_TOPIC".fromEnv()

    object Auth {
        val discoveryUrl = "IDPORTEN_WELL_KNOWN_URL".fromEnv()
        val acceptedAudience = "IDPORTEN_AUDIENCE".fromEnv().let(::listOf)
    }

    object Redis {
        val host = "REDIS_HOST_INNTEKTSMELDING".fromEnv()
        val port = "REDIS_PORT_INNTEKTSMELDING".fromEnv().toInt()
        val username = "REDIS_USERNAME_INNTEKTSMELDING".fromEnv()
        val password = "REDIS_PASSWORD_INNTEKTSMELDING".fromEnv()
    }
}
