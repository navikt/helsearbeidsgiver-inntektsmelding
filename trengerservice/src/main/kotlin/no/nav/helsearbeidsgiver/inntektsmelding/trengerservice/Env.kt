package no.nav.helsearbeidsgiver.inntektsmelding.trengerservice

import no.nav.helsearbeidsgiver.felles.fromEnv

object Env {
    val redisUrl = "REDIS_URL".fromEnv()
}
