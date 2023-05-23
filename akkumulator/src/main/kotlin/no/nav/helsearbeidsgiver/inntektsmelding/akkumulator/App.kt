package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.RedisStore
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

val logger = "helsearbeidsgiver-im-akkumulator".logger()
val sikkerLogger = sikkerLogger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createAkkumulator(buildRedisStore(setUpEnvironment()))
        .start()
}

fun RapidsConnection.createAkkumulator(redisStore: RedisStore): RapidsConnection {
    logger.info("Starting Akkumulator...")
    Akkumulator(this, redisStore)
    return this
}

fun buildRedisStore(environment: Environment): RedisStore {
    return RedisStore(environment.redisUrl)
}
