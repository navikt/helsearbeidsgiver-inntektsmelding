package no.nav.helsearbeidsgiver.inntektsmelding.aktiveorgnrservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateful
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-aktiveorgnrservice".logger()

fun main() {
    val redisConnection = RedisConnection(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createAktiveOrgnrService(redisConnection)
        .registerShutdownLifecycle {
            redisConnection.close()
        }.start()
}

fun RapidsConnection.createAktiveOrgnrService(redisConnection: RedisConnection): RapidsConnection =
    also {
        logger.info("Starter ${AktiveOrgnrService::class.simpleName}...")
        val redisStore = RedisStore(redisConnection, RedisPrefix.AktiveOrgnr)

        ServiceRiverStateful(
            redisStore = redisStore,
            service =
                AktiveOrgnrService(
                    rapid = this,
                    redisStore = redisStore,
                ),
        ).connect(this)
    }
