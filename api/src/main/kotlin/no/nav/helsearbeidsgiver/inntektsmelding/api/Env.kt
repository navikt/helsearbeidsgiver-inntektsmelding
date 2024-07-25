package no.nav.helsearbeidsgiver.inntektsmelding.api

import no.nav.helsearbeidsgiver.felles.fromEnv

object Env {
    object Auth {
        val discoveryUrl: String = "IDPORTEN_WELL_KNOWN_URL".fromEnv()
        val acceptedAudience: List<String> = "IDPORTEN_AUDIENCE".fromEnv().let(::listOf)
        object TokenX {
            val discoveryUrl : String = "TOKEN_X_WELL_KNOWN_URL".fromEnv()
            val acceptedAudience: List<String> = "TOKEN_X_CLIENT_ID".fromEnv().let(::listOf)
        }
    }

    object Redis {
        val url: String = "REDIS_URL".fromEnv()
    }
}
