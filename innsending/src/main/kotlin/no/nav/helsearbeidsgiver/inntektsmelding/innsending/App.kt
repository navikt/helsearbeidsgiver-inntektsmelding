package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiver
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-innsending".logger()

fun main() {
    val redisConnection = RedisConnection(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createInnsending(redisConnection)
        .registerShutdownLifecycle {
            redisConnection.close()
        }.start()
}

fun RapidsConnection.createInnsending(redisConnection: RedisConnection): RapidsConnection =
    also {
        logger.info("Starter ${InnsendingService::class.simpleName}...")
        ServiceRiver(
            InnsendingService(
                rapid = this,
                redisStore = RedisStoreClassSpecific(redisConnection, RedisPrefix.InnsendingService),
            ),
        ).connect(this)

        logger.info("Starter ${KvitteringService::class.simpleName}...")
        ServiceRiver(
            KvitteringService(
                rapid = this,
                redisStore = RedisStoreClassSpecific(redisConnection, RedisPrefix.KvitteringService),
            ),
        ).connect(this)
    }
