package no.nav.helsearbeidsgiver.inntektsmelding.aktiveorgnrservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-aktiveorgnrservice".logger()

fun main() {
    val redisStore = RedisStore(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createAktiveOrgnrService(redisStore)
        .registerShutdownLifecycle {
            redisStore.shutdown()
        }
        .start()
}

fun RapidsConnection.createAktiveOrgnrService(redisStore: RedisStore): RapidsConnection =
    also {
        logger.info("Starter ${AktiveOrgnrService::class.simpleName}...")
        AktiveOrgnrService(this, redisStore)
    }
