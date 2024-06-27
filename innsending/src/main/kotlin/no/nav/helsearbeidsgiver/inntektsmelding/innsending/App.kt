package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiver
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

private val logger = "helsearbeidsgiver-im-innsending".logger()
val sikkerLogger = sikkerLogger()

fun main() {
    val redisConnection = RedisConnection(Env.redisUrl)
    val redisStoreClassSpecific = RedisStoreClassSpecific(redisConnection, RedisPrefix.InnsendingService)

    val redisStore = RedisStore(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createInnsendingService(redisStoreClassSpecific)
        .createKvitteringService(redisStore)
        .registerShutdownLifecycle {
            redisStore.shutdown()
        }
        .start()
}

fun RapidsConnection.createInnsendingService(redisStore: RedisStoreClassSpecific): RapidsConnection =
    also {
        logger.info("Starter ${InnsendingService::class.simpleName}...")
        ServiceRiver(
            InnsendingService(this, redisStore),
        ).connect(this)
    }

fun RapidsConnection.createKvitteringService(redisStore: RedisStore): RapidsConnection =
    also {
        logger.info("Starter ${KvitteringService::class.simpleName}...")
        KvitteringService(this, redisStore)
    }
