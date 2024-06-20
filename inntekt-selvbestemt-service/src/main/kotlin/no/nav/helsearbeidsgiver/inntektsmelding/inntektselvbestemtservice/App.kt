package no.nav.helsearbeidsgiver.inntektsmelding.inntektselvbestemtservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiver
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-inntektselvbestemtservice".logger()

fun main() {
    val redisConnection = RedisConnection(Env.redisUrl)
    val redisStore = RedisStoreClassSpecific(redisConnection, RedisPrefix.InntektSelvbestemtService)

    RapidApplication
        .create(System.getenv())
        .createInntektSelvbestemtService(redisStore)
        .registerShutdownLifecycle {
            redisConnection.close()
        }
        .start()
}

fun RapidsConnection.createInntektSelvbestemtService(redisStore: RedisStoreClassSpecific): RapidsConnection =
    also {
        logger.info("Starter ${InntektSelvbestemtService::class.simpleName}...")
        ServiceRiver(
            InntektSelvbestemtService(this, redisStore)
        ).connect(this)
    }
