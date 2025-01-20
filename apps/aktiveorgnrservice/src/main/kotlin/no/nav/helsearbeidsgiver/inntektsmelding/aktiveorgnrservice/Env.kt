package no.nav.helsearbeidsgiver.inntektsmelding.aktiveorgnrservice

import no.nav.helsearbeidsgiver.felles.utils.fromEnv

object Env {
    val redisHost = "REDIS_HOST_INNTEKTSMELDING".fromEnv()
    val redisPort = "REDIS_PORT_INNTEKTSMELDING".fromEnv().toInt()
    val redisUsername = "REDIS_USERNAME_INNTEKTSMELDING".fromEnv()
    val redisPassword = "REDIS_PASSWORD_INNTEKTSMELDING".fromEnv()
}
