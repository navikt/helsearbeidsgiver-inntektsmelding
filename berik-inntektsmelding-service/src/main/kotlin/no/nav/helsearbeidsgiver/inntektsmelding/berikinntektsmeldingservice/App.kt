package no.nav.helsearbeidsgiver.inntektsmelding.berikinntektsmeldingservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateful
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-berikinntektsmeldingservice".logger()

fun main() {
    val redisConnection = RedisConnection(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createBerikInntektsmeldingService(redisConnection)
        .registerShutdownLifecycle {
            redisConnection.close()
        }.start()
}

fun RapidsConnection.createBerikInntektsmeldingService(redisConnection: RedisConnection): RapidsConnection =
    also {
        logger.info("Starter ${BerikInntektsmeldingService::class.simpleName}...")
        ServiceRiverStateful(
            BerikInntektsmeldingService(
                rapid = this,
                redisStore = RedisStore(redisConnection, RedisPrefix.BerikInntektsmeldingService),
            ),
        ).connect(this)
    }
