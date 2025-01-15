package no.nav.helsearbeidsgiver.inntektsmelding.trengerservice

import no.nav.helsearbeidsgiver.felles.utils.fromEnv

object Env {
    val redisUri = "REDIS_URI_INNTEKTSMELDING".fromEnv()
}
