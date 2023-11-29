package no.nav.helsearbeidsgiver.inntektsmelding.inntektservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-inntektservice".logger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createInntektService(RedisStore(Env.redisUrl))
        .start()
}

fun RapidsConnection.createInntektService(redisStore: RedisStore): RapidsConnection =
    also {
        logger.info("Starter ${InntektService::class.simpleName}...")
        InntektService(this, redisStore)
    }
