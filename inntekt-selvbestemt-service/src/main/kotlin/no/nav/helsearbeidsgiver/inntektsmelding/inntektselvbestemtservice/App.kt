package no.nav.helsearbeidsgiver.inntektsmelding.inntektselvbestemtservice

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
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
        ServiceRiverStateless(
            InntektSelvbestemtService(
                rapid = this,
                redisStore = RedisStore(redisConnection, RedisPrefix.InntektSelvbestemt),
            ),
        ).connect(this)
    }
