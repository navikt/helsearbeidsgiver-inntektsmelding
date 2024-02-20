package no.nav.helsearbeidsgiver.inntektsmelding.aapenimservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-aapenimservice".logger()

fun main() {
    val redisStore = RedisStore(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createAapenImService(redisStore)
        .registerShutdownLifecycle {
            redisStore.shutdown()
        }
        .start()
}

fun RapidsConnection.createAapenImService(redisStore: RedisStore): RapidsConnection =
    also {
        logger.info("Starter ${HentAapenImService::class.simpleName}...")
        HentAapenImService(this, redisStore)

        logger.info("Starter ${LagreAapenImService::class.simpleName}...")
        LagreAapenImService(this, redisStore)
    }
