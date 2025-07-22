package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemthentimservice

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.rapidsrivers.onShutdown
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-selvbestemt-hent-im-service".logger()

fun main() {
    val redisConnection =
        RedisConnection(
            host = Env.redisHost,
            port = Env.redisPort,
            username = Env.redisUsername,
            password = Env.redisPassword,
        )

    RapidApplication
        .create(System.getenv())
        .createHentSelvbestemtImService(redisConnection)
        .onShutdown {
            redisConnection.close()
        }.start()
}

fun RapidsConnection.createHentSelvbestemtImService(redisConnection: RedisConnection): RapidsConnection =
    also {
        logger.info("Starter ${HentSelvbestemtImService::class.simpleName}...")
        ServiceRiverStateless(
            HentSelvbestemtImService(
                rapid = this,
                redisStore = RedisStore(redisConnection, RedisPrefix.HentSelvbestemtIm),
            ),
        ).connect(this)
    }
