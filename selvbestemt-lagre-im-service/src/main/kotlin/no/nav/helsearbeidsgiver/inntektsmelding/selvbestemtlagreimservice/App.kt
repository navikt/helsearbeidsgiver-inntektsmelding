package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemtlagreimservice

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
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
        ServiceRiverStateful(
            LagreSelvbestemtImService(
                rapid = this,
                redisStore = RedisStore(redisConnection, RedisPrefix.LagreSelvbestemtIm),
            ),
        ).connect(this)
    }
