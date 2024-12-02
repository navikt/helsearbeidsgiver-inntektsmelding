package no.nav.helsearbeidsgiver.inntektsmelding.api

import no.nav.helsearbeidsgiver.felles.utils.fromEnv

object Env {
    object Auth {
        val discoveryUrl: String = "IDPORTEN_WELL_KNOWN_URL".fromEnv()
        val acceptedAudience: List<String> = "IDPORTEN_AUDIENCE".fromEnv().let(::listOf)
    }

    object Redis {
        val url: String = "REDIS_URL".fromEnv()
    }
}
