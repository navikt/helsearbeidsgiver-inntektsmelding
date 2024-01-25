package no.nav.helsearbeidsgiver.inntektsmelding.aapenimservice

import no.nav.helsearbeidsgiver.felles.fromEnv

object Env {
    val redisUrl = "REDIS_URL".fromEnv()
}
