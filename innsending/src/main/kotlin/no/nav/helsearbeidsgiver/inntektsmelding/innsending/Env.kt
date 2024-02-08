package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helsearbeidsgiver.felles.fromEnv

object Env {
    val redisUrl = "REDIS_URL".fromEnv()
}
