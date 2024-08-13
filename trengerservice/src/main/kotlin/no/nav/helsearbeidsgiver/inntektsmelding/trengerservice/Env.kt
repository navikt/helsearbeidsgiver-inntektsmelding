package no.nav.helsearbeidsgiver.inntektsmelding.trengerservice

import no.nav.helsearbeidsgiver.felles.utils.fromEnv

object Env {
    val redisUrl = "REDIS_URL".fromEnv()
}
