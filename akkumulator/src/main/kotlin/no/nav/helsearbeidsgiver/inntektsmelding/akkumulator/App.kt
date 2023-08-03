package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.trenger.TrengerService
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-akkumulator".logger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createAkkumulator(buildRedisStore(setUpEnvironment()))
        .start()
}

fun RapidsConnection.createAkkumulator(redisStore: IRedisStore): RapidsConnection =
    also {
        logger.info("Starting TrengerService...")
        TrengerService(this, redisStore)
    }

fun buildRedisStore(environment: Environment): IRedisStore =
    RedisStore(environment.redisUrl)
