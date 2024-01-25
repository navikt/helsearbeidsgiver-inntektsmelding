package no.nav.helsearbeidsgiver.inntektsmelding.aapenimservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-aapenimservice".logger()

fun main() {
    val redisStore = RedisStore(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createAapenImService(redisStore)
        .start()
}

fun RapidsConnection.createAapenImService(redisStore: RedisStore): RapidsConnection =
    also {
        logger.info("Starter ${AapenImService::class.simpleName}...")
        AapenImService(this, redisStore)
    }
