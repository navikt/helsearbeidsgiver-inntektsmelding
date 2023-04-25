package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
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
    TestCompositeEventListener(rapidsConnection, EventName.EVENT_FOR_TEST, redisClient)
        .withFailKanal { DelegatingFailKanal(EventName.INSENDING_STARTED, it, rapidsConnection) }
        .withEventListener { InnsendingService.InnsendingStartedListener(it, rapidsConnection) }
        .withDataKanal { StatefullDataKanal(DataFelter.values().map { it.str }.toTypedArray(), EventName.INSENDING_STARTED, it, rapidsConnection, redisClient) }
    return rapidsConnection
}

fun RapidsConnection.createInnsending(redisStore: RedisStore): RapidsConnection {
    InnsendingService(this, redisStore)
    KvitteringService(this, redisStore)
    return this
}

fun buildRedisStore(environment: Environment): RedisStore {
    sikkerlogg.info("Redis url er " + environment.redisUrl)
    return RedisStore(environment.redisUrl)
}
