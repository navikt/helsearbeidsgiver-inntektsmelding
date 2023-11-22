package no.nav.helsearbeidsgiver.inntektsmelding.trengerservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-trengerservice".logger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createTrengerService(RedisStore(Env.redisUrl))
        .start()
}

fun RapidsConnection.createTrengerService(redisStore: RedisStore): RapidsConnection =
    also {
        logger.info("Starter ${TrengerService::class.simpleName}...")
        TrengerService(this, redisStore)
    }
