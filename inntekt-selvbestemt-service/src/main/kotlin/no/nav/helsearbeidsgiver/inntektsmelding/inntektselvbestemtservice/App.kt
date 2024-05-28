package no.nav.helsearbeidsgiver.inntektsmelding.inntektselvbestemtservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiver
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-inntektselvbestemtservice".logger()

fun main() {
    val redisStore = RedisStore(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createInntektSelvbestemtService(redisStore)
        .registerShutdownLifecycle {
            redisStore.shutdown()
        }
        .start()
}

fun RapidsConnection.createInntektSelvbestemtService(redisStore: RedisStore): RapidsConnection =
    also {
        logger.info("Starter ${InntektSelvbestemtService::class.simpleName}...")
        ServiceRiver(
            InntektSelvbestemtService(this, redisStore)
        ).connect(this)
    }
