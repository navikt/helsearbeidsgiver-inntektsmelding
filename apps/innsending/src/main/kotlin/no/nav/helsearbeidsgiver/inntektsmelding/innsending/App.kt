package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

private val logger = "helsearbeidsgiver-im-innsending".logger()
val sikkerLogger = sikkerLogger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createInnsending(buildRedisStore(setUpEnvironment()))
        .start()
}

fun RapidsConnection.createInnsending(redisStore: IRedisStore): RapidsConnection =
    also {
        logger.info("Starter ${InnsendingService::class.simpleName}...")
        InnsendingService(this, redisStore)

        logger.info("Starter ${KvitteringService::class.simpleName}...")
        KvitteringService(this, redisStore)
    }

fun buildRedisStore(environment: Environment): IRedisStore {
    sikkerLogger.info("Redis url er " + environment.redisUrl)
    return RedisStore(environment.redisUrl)
}
