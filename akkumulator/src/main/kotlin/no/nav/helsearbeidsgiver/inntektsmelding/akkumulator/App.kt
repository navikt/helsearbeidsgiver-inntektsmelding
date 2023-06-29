package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.trenger.TrengerService
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

fun RapidsConnection.createAkkumulator(redisStore: IRedisStore): RapidsConnection {
    logger.info("Starting Akkumulator...")
    Akkumulator(this, redisStore)
    TrengerService(this, redisStore)
    return this
}

fun buildRedisStore(environment: Environment): IRedisStore {
    return RedisStore(environment.redisUrl)
}
