package no.nav.helsearbeidsgiver.inntektsmelding.inntektservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-inntektservice".logger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createInntektService(RedisStore(Env.redisUrl))
        .start()
}

fun RapidsConnection.createInntektService(redisStore: IRedisStore): RapidsConnection =
    also {
        logger.info("Starting InntektService...")
        InntektService(this, redisStore)
    }
