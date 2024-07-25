package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateful
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
        val redisStoreInnsending = RedisStore(redisConnection, RedisPrefix.Innsending)

        ServiceRiverStateful(
            redisStore = redisStoreInnsending,
            service =
                InnsendingService(
                    rapid = this,
                    redisStore = redisStoreInnsending,
                ),
        ).connect(this)

        logger.info("Starter ${KvitteringService::class.simpleName}...")
        val redisStoreKvittering = RedisStore(redisConnection, RedisPrefix.Kvittering)

        ServiceRiverStateful(
            redisStore = redisStoreKvittering,
            service =
                KvitteringService(
                    rapid = this,
                    redisStore = redisStoreKvittering,
                ),
        ).connect(this)
    }
