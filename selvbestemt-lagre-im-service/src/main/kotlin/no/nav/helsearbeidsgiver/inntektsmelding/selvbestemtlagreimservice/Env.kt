package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemtlagreimservice

import no.nav.helsearbeidsgiver.felles.utils.fromEnv

object Env {
    val redisUrl = "REDIS_URL".fromEnv()
}
