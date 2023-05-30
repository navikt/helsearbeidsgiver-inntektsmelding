package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.RedisStore
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

fun createApp(environment: Environment): RapidsConnection {
    logger.info("Starting Redis client...")
    logger.info("Redis url er " + environment.redisUrl)
    val redisClient = RedisStore(environment.redisUrl)
    logger.info("Starting RapidApplication...")
    val rapidsConnection = RapidApplication.create(environment.raw)
    logger.info("Starting Innsending...")
    InnsendingService(rapidsConnection, redisClient)
    KvitteringServiceExperimental(rapidsConnection, redisClient)
    return rapidsConnection
}

fun RapidsConnection.createInnsending(redisStore: IRedisStore): RapidsConnection {
    InnsendingService(this, redisStore)
    KvitteringServiceExperimental(this, redisStore)
    return this
}

fun buildRedisStore(environment: Environment): IRedisStore {
    sikkerLogger.info("Redis url er " + environment.redisUrl)
    return RedisStore(environment.redisUrl)
}
