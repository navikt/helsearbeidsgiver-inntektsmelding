package no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateful
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
        val redisStoreTilgangForespoersel = RedisStore(redisConnection, RedisPrefix.TilgangForespoersel)

        ServiceRiverStateful(
            redisStore = redisStoreTilgangForespoersel,
            service =
                TilgangForespoerselService(
                    rapid = this,
                    redisStore = redisStoreTilgangForespoersel,
                ),
        ).connect(this)

        logger.info("Starter ${TilgangOrgService::class.simpleName}...")
        val redisStoreTilgangOrg = RedisStore(redisConnection, RedisPrefix.TilgangOrg)

        ServiceRiverStateful(
            redisStore = redisStoreTilgangOrg,
            service =
                TilgangOrgService(
                    rapid = this,
                    redisStore = redisStoreTilgangOrg,
                ),
        ).connect(this)
    }
