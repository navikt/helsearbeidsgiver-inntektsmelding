package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemtlagreimservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-selvbestemt-lagre-im-service".logger()

fun main() {
    val redisStore = RedisStore(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createLagreSelvbestemtImService(redisStore)
        .registerShutdownLifecycle {
            redisStore.shutdown()
        }
        .start()
}

fun RapidsConnection.createLagreSelvbestemtImService(redisStore: RedisStore): RapidsConnection =
    also {
        logger.info("Starter ${LagreSelvbestemtImService::class.simpleName}...")
        LagreSelvbestemtImService(this, redisStore)
    }
