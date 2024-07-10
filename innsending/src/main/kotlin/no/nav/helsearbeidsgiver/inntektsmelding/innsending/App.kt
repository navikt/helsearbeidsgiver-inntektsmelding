package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

private val logger = "helsearbeidsgiver-im-innsending".logger()
val sikkerLogger = sikkerLogger()

fun main() {
    val redisStore = RedisStore(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createInnsending(redisStore)
        .registerShutdownLifecycle {
            redisStore.shutdown()
        }.start()
}

fun RapidsConnection.createInnsending(redisStore: RedisStore): RapidsConnection =
    also {
        logger.info("Starter ${InnsendingService::class.simpleName}...")
        InnsendingService(this, redisStore)

        logger.info("Starter ${KvitteringService::class.simpleName}...")
        KvitteringService(this, redisStore)
    }
