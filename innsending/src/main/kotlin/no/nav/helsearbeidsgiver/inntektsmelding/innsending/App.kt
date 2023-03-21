package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("innsending")

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
    return rapidsConnection
}

fun RapidsConnection.createInnsending(redisStore: RedisStore): RapidsConnection {
    InnsendingService(this, redisStore)
    return this
}

fun buildRedisStore(environment: Environment): RedisStore {
    logger.info("Redis url er " + environment.redisUrl)
    return RedisStore(environment.redisUrl)
}
