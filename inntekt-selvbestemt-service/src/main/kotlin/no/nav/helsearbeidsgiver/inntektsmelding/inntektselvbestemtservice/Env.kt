package no.nav.helsearbeidsgiver.inntektsmelding.inntektselvbestemtservice

import no.nav.helsearbeidsgiver.felles.fromEnv

object Env {
    val redisUrl = "REDIS_URL".fromEnv()
}
