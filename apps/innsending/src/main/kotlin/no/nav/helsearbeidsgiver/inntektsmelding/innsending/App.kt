package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateful
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.api.ApiInnsendingService
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-innsending".logger()

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
        .createInnsending(redisConnection)
        .registerShutdownLifecycle {
            redisConnection.close()
        }.start()
}

fun RapidsConnection.createInnsending(redisConnection: RedisConnection): RapidsConnection =
    also {
        logger.info("Starter ${InnsendingService::class.simpleName}...")
        ServiceRiverStateless(
            InnsendingService(
                rapid = this,
                redisStore = RedisStore(redisConnection, RedisPrefix.Innsending),
            ),
        ).connect(this)

        // TODO: Enable i prod når vi kobler til nytt kafka-topic
        if ("dev-gcp".equals(System.getenv()["NAIS_CLUSTER_NAME"], ignoreCase = true)) {
            logger.info("Starter ${ApiInnsendingService::class.simpleName}...")
            ServiceRiverStateless(
                ApiInnsendingService(
                    rapid = this,
                    redisStore = RedisStore(redisConnection, RedisPrefix.ApiInnsending),
                ),
            ).connect(this)
        }

        logger.info("Starter ${KvitteringService::class.simpleName}...")
        ServiceRiverStateful(
            KvitteringService(
                rapid = this,
                redisStore = RedisStore(redisConnection, RedisPrefix.Kvittering),
            ),
        ).connect(this)
    }
