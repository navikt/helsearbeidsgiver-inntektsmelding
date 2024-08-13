package no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice

import no.nav.helsearbeidsgiver.felles.utils.fromEnv

object Env {
    val redisUrl = "REDIS_URL".fromEnv()
}
