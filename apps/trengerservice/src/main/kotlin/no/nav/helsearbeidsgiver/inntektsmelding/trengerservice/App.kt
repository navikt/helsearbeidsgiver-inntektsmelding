package no.nav.helsearbeidsgiver.inntektsmelding.trengerservice

import no.nav.hag.simba.utils.rr.Publisher
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.hag.simba.utils.rr.service.ServiceRiverStateful
import no.nav.hag.simba.utils.rr.service.ServiceRiverStateless
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore

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
        createHentForespoerselServices(it, redisConnection)
    }
}

fun createHentForespoerselServices(
    publisher: Publisher,
    redisConnection: RedisConnection,
): List<ObjectRiver.Simba<*>> =
    listOf(
        ServiceRiverStateful(
            HentForespoerselService(
                publisher = publisher,
                redisStore = RedisStore(redisConnection, RedisPrefix.HentForespoersel),
            ),
        ),
        ServiceRiverStateless(
            HentForespoerslerForVedtaksperiodeIdListeService(
                publisher = publisher,
                redisStore = RedisStore(redisConnection, RedisPrefix.HentForespoerslerForVedtaksperiodeIdListe),
            ),
        ),
    )
