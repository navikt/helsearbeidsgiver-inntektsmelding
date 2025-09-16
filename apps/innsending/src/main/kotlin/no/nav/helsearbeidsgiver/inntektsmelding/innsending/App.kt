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
    val producer = Producer(topic = "helsearbeidsgiver.api-innsending")

    val redisConnection =
        RedisConnection(
            host = Env.redisHost,
            port = Env.redisPort,
            username = Env.redisUsername,
            password = Env.redisPassword,
        )

    val isDev = "dev-gcp".equals(System.getenv()["NAIS_CLUSTER_NAME"], ignoreCase = true)

    ObjectRiver.connectToRapid(
        onShutdown = { redisConnection.close() },
    ) {
        createInnsendingServices(
            publisher = it,
            redisConnection = redisConnection,
            producer = producer,
            taImotEksternInnsending = isDev,
        )
    }
}

fun createInnsendingServices(
    publisher: Publisher,
    redisConnection: RedisConnection,
    producer: Producer,
    taImotEksternInnsending: Boolean,
): List<ObjectRiver.Simba<*>> {
    val innsendingServiceRiver =
        ServiceRiverStateless(
            InnsendingService(
                publisher = publisher,
                redisStore = RedisStore(redisConnection, RedisPrefix.Innsending),
            ),
        )
    val valideringsServiceRiver =
        if (taImotEksternInnsending) {
            ServiceRiverStateless(
                ValiderApiInnsendingService(
                    publisher = publisher,
                    producer = producer,
                ),
            )
        } else {
            null
        }

    val apiInnsendingServiceRiver =
        if (taImotEksternInnsending) {
            ServiceRiverStateless(ApiInnsendingService(publisher = publisher))
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
