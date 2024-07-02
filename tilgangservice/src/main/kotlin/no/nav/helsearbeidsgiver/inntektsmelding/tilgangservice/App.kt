package no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiver
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-tilgangservice".logger()

fun main() {
    val redisConnection = RedisConnection(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createTilgangService(redisConnection)
        .registerShutdownLifecycle {
            redisConnection.close()
        }
        .start()
}

fun RapidsConnection.createTilgangService(redisConnection: RedisConnection): RapidsConnection =
    also {
        logger.info("Starter ${TilgangForespoerselService::class.simpleName}...")
        ServiceRiver(
            TilgangForespoerselService(
                rapid = this,
                redisStore = RedisStoreClassSpecific(redisConnection, RedisPrefix.TilgangForespoerselService)
            )
        ).connect(this)

        logger.info("Starter ${TilgangOrgService::class.simpleName}...")
        ServiceRiver(
            TilgangOrgService(
                rapid = this,
                redisStore = RedisStoreClassSpecific(redisConnection, RedisPrefix.TilgangOrgService)
            )
        ).connect(this)
    }
