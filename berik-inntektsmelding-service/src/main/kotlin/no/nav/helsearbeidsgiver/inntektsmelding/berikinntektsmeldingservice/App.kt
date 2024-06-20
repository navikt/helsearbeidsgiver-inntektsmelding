package no.nav.helsearbeidsgiver.inntektsmelding.berikinntektsmeldingservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiver
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-berikinntektsmeldingservice".logger()

fun main() {
    val redisConnection = RedisConnection(Env.redisUrl)
    val redisStore = RedisStoreClassSpecific(redisConnection, RedisPrefix.BerikInntektsmeldingService)

    RapidApplication
        .create(System.getenv())
        .createBerikInntektsmeldingService(redisStore)
        .registerShutdownLifecycle {
            redisConnection.close()
        }
        .start()
}

fun RapidsConnection.createBerikInntektsmeldingService(redisStore: RedisStoreClassSpecific): RapidsConnection =
    also {
        logger.info("Starter ${BerikInntektsmeldingService::class.simpleName}...")
        ServiceRiver(
            BerikInntektsmeldingService(this, redisStore)
        ).connect(this)
    }
