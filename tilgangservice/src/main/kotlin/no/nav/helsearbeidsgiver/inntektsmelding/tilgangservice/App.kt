package no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateful
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-tilgangservice".logger()

fun main() {
    val redisConnection = RedisConnection(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createTilgangService(redisConnection)
        .registerShutdownLifecycle {
            redisConnection.close()
        }.start()
}

fun RapidsConnection.createTilgangService(redisConnection: RedisConnection): RapidsConnection =
    also {
        logger.info("Starter ${TilgangForespoerselService::class.simpleName}...")
        ServiceRiverStateful(
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
