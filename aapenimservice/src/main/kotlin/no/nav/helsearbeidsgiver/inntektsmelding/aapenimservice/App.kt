package no.nav.helsearbeidsgiver.inntektsmelding.aapenimservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiver
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-selvbestemtimservice".logger()

fun main() {
    val redisConnection = RedisConnection(Env.redisUrl)
    val redisStore = RedisStore(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createSelvbestemtImService(redisConnection, redisStore)
        .registerShutdownLifecycle {
            redisConnection.close()
            redisStore.shutdown()
        }
        .start()
}

fun RapidsConnection.createSelvbestemtImService(redisConnection: RedisConnection, redisStore: RedisStore): RapidsConnection =
    also {
        logger.info("Starter ${HentSelvbestemtImService::class.simpleName}...")
        ServiceRiver(
            HentSelvbestemtImService(
                rapid = this,
                redisStore = RedisStoreClassSpecific(redisConnection, RedisPrefix.HentSelvbestemtImService)
            )
        )
            .connect(this)

        logger.info("Starter ${LagreSelvbestemtImService::class.simpleName}...")
        LagreSelvbestemtImService(this, redisStore)
    }
