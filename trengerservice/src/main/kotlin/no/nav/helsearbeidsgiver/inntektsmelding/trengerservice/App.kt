package no.nav.helsearbeidsgiver.inntektsmelding.trengerservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiver
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-hent-forespoersel-service".logger()

fun main() {
    val redisConnection = RedisConnection(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createHentForespoerselService(redisConnection)
        .registerShutdownLifecycle {
            redisConnection.close()
        }.start()
}

fun RapidsConnection.createHentForespoerselService(redisConnection: RedisConnection): RapidsConnection =
    also {
        logger.info("Starter ${HentForespoerselService::class.simpleName}...")
        ServiceRiver(
            HentForespoerselService(
                rapid = this,
                redisStore = RedisStore(redisConnection, RedisPrefix.HentForespoersel),
            ),
        ).connect(this)
    }
