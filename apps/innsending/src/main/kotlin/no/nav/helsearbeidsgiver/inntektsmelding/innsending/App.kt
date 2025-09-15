package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.rr.Publisher
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.hag.simba.utils.rr.service.ServiceRiverStateful
import no.nav.hag.simba.utils.rr.service.ServiceRiverStateless
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.ekstern.ApiInnsendingService
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.ekstern.ValiderApiInnsendingService

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
): List<ObjectRiver.Simba<*>> {
    val isDev = "dev-gcp".equals(System.getenv()["NAIS_CLUSTER_NAME"], ignoreCase = true)

    val innsendingServiceRiver =
        ServiceRiverStateless(
            InnsendingService(
                publisher = publisher,
                redisStore = RedisStore(redisConnection, RedisPrefix.Innsending),
            ),
        )
    val apiInnsendingServiceRiver =
        if (isDev) {
            ServiceRiverStateless(ApiInnsendingService(publisher = publisher))
        } else {
            null
        }

    val valideringsServiceRiver =
        if (isDev) {
            ServiceRiverStateless(
                ValiderApiInnsendingService(
                    publisher = publisher,
                    producer = Producer(topic = "helsearbeidsgiver.api-innsending"),
                ),
            )
        } else {
            null
        }

    val kvitteringServiceRiver =
        ServiceRiverStateful(
            KvitteringService(
                publisher = publisher,
                redisStore = RedisStore(redisConnection, RedisPrefix.Kvittering),
            ),
        )

    return listOfNotNull(
        innsendingServiceRiver,
        apiInnsendingServiceRiver,
        valideringsServiceRiver,
        kvitteringServiceRiver,
    )
}
