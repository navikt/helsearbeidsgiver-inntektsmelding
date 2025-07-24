package no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.rapidsrivers.onShutdown
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-tilgangservice".logger()

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
        .createTilgangService(redisConnection)
        .onShutdown {
            redisConnection.close()
        }.start()
}

fun RapidsConnection.createTilgangService(redisConnection: RedisConnection): RapidsConnection =
    also {
        logger.info("Starter ${TilgangForespoerselService::class.simpleName}...")
        ServiceRiverStateless(
            TilgangForespoerselService(
                rapid = this,
                redisStore = RedisStore(redisConnection, RedisPrefix.TilgangForespoersel),
            ),
        ).connect(this)

        logger.info("Starter ${TilgangOrgService::class.simpleName}...")
        ServiceRiverStateless(
            TilgangOrgService(
                rapid = this,
                redisStore = RedisStore(redisConnection, RedisPrefix.TilgangOrg),
            ),
        ).connect(this)
    }
