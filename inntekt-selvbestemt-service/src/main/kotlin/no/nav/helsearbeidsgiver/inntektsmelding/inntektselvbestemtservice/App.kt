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

    RapidApplication
        .create(System.getenv())
        .createInntektSelvbestemtService(redisConnection)
        .registerShutdownLifecycle {
            redisConnection.close()
        }.start()
}

fun RapidsConnection.createInntektSelvbestemtService(redisConnection: RedisConnection): RapidsConnection =
    also {
        logger.info("Starter ${InntektSelvbestemtService::class.simpleName}...")
        ServiceRiver(
            InntektSelvbestemtService(
                rapid = this,
                redisStore = RedisStoreClassSpecific(redisConnection, RedisPrefix.InntektSelvbestemtService),
            ),
        ).connect(this)
    }
