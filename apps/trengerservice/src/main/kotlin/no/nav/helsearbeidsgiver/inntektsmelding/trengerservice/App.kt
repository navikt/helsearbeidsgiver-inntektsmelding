package no.nav.helsearbeidsgiver.inntektsmelding.trengerservice

import no.nav.helsearbeidsgiver.felles.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rr.Publisher
import no.nav.helsearbeidsgiver.felles.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.rr.service.ServiceRiverStateful
import no.nav.helsearbeidsgiver.felles.rr.service.ServiceRiverStateless

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
