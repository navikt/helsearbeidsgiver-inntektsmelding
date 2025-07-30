package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helsearbeidsgiver.felles.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rr.Publisher
import no.nav.helsearbeidsgiver.felles.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.rr.service.ServiceRiverStateful
import no.nav.helsearbeidsgiver.felles.rr.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.api.ApiInnsendingService
import no.nav.helsearbeidsgiver.utils.log.logger

fun main() {
    val redisConnection =
        RedisConnection(
            host = Env.redisHost,
            port = Env.redisPort,
            username = Env.redisUsername,
            password = Env.redisPassword,
        )

    ObjectRiver.connectToRapid(
        onShutdown = { redisConnection.close() },
    ) {
        createInnsendingServices(it, redisConnection)
    }
}

fun createInnsendingServices(
    publisher: Publisher,
    redisConnection: RedisConnection,
): List<ObjectRiver.Simba<*>> =
    listOfNotNull(
        ServiceRiverStateless(
            InnsendingService(
                publisher = publisher,
                redisStore = RedisStore(redisConnection, RedisPrefix.Innsending),
            ),
        ),
        // TODO: Enable i prod når vi kobler til nytt kafka-topic
        if ("dev-gcp".equals(System.getenv()["NAIS_CLUSTER_NAME"], ignoreCase = true)) {
            val logger = "helsearbeidsgiver-im-innsending".logger()
            logger.info("Starter ${ApiInnsendingService::class.simpleName}...")

            ServiceRiverStateless(
                ApiInnsendingService(
                    publisher = publisher,
                    redisStore = RedisStore(redisConnection, RedisPrefix.ApiInnsending),
                ),
            )
        } else {
            null
        },
        ServiceRiverStateful(
            KvitteringService(
                publisher = publisher,
                redisStore = RedisStore(redisConnection, RedisPrefix.Kvittering),
            ),
        ),
    )
