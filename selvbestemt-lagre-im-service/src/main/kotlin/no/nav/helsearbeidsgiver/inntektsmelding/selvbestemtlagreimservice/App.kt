package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemtlagreimservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateful
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-selvbestemt-lagre-im-service".logger()

fun main() {
    val redisConnection = RedisConnection(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createLagreSelvbestemtImService(redisConnection)
        .registerShutdownLifecycle {
            redisConnection.close()
        }.start()
}

fun RapidsConnection.createLagreSelvbestemtImService(redisConnection: RedisConnection): RapidsConnection =
    also {
        logger.info("Starter ${LagreSelvbestemtImService::class.simpleName}...")
        val redisStore = RedisStore(redisConnection, RedisPrefix.LagreSelvbestemtIm)

        ServiceRiverStateful(
            redisStore = redisStore,
            service =
                LagreSelvbestemtImService(
                    rapid = this,
                    redisStore = redisStore,
                ),
        ).connect(this)
    }
