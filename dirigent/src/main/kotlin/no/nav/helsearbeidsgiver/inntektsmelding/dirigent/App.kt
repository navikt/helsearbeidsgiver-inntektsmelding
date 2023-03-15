package no.nav.helsearbeidsgiver.inntektsmelding.dirigent

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.log.logger

private val logger = "helsearbeidsgiver-im-dirigent".logger()

fun main() {
    logger.info("Starting Redis client...")
    val mockRedisStore = MockRedisStore(Env.redisUrl)

    logger.info("Starting RapidApplication...")
    val rapid = RapidApplication.create(System.getenv())

    logger.info("Starting ApiBehovRiver...")
    ApiBehovRiver(rapid, mockRedisStore)

    logger.info("Starting Dirigent...")
    Dirigent(rapid, mockRedisStore)

    rapid
        .registerShutdownLifecycle(mockRedisStore)
        .start()
}

private fun RapidsConnection.registerShutdownLifecycle(redis: MockRedisStore): RapidsConnection =
    also {
        register(object : RapidsConnection.StatusListener {
            override fun onShutdown(rapidsConnection: RapidsConnection) {
                redis.shutdown()
            }
        })
    }
