package no.nav.helsearbeidsgiver.inntektsmelding.aapenimservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-selvbestemtimservice".logger()

fun main() {
    val redisStore = RedisStore(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createSelvbestemtImService(redisStore)
        .registerShutdownLifecycle {
            redisStore.shutdown()
        }
        .start()
}

fun RapidsConnection.createSelvbestemtImService(redisStore: RedisStore): RapidsConnection =
    also {
        logger.info("Starter ${HentSelvbestemtImService::class.simpleName}...")
        HentSelvbestemtImService(this, redisStore)

        logger.info("Starter ${LagreSelvbestemtImService::class.simpleName}...")
        LagreSelvbestemtImService(this, redisStore)
    }
