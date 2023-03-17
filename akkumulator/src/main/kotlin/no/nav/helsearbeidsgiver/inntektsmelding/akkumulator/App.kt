package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-akkumulator")

fun main() {
    RapidApplication
        .create(System.getenv())
        .createAkkumulator(buildRedisStore(setUpEnvironment()))
        .start()
}

fun RapidsConnection.createAkkumulator(redisStore: RedisStore): RapidsConnection {
    sikkerlogg.info("Starting Akkumulator...")
    Akkumulator(this, redisStore)
    return this
}

fun buildRedisStore(environment: Environment): RedisStore {
    return RedisStore(environment.redisUrl)
}
