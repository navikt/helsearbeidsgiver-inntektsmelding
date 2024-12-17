package no.nav.helsearbeidsgiver.inntektsmelding.inntektservice

import no.nav.helsearbeidsgiver.felles.utils.fromEnv

object Env {
    val redisUrl = "REDIS_URL".fromEnv()
}
