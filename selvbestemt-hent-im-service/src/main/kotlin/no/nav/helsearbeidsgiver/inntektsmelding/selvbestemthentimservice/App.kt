package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemthentimservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiver
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-selvbestemt-hent-im-service".logger()

fun main() {
    val redisConnection = RedisConnection(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createHentSelvbestemtImService(redisConnection)
        .registerShutdownLifecycle {
            redisConnection.close()
        }.start()
}

fun RapidsConnection.createHentSelvbestemtImService(redisConnection: RedisConnection): RapidsConnection =
    also {
        logger.info("Starter ${HentSelvbestemtImService::class.simpleName}...")
        ServiceRiver(
            HentSelvbestemtImService(
                rapid = this,
                redisStore = RedisStoreClassSpecific(redisConnection, RedisPrefix.HentSelvbestemtImService),
            ),
        ).connect(this)
    }
