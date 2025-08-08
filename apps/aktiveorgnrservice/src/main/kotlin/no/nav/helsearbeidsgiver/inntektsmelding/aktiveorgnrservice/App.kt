package no.nav.helsearbeidsgiver.inntektsmelding.aktiveorgnrservice

import no.nav.helsearbeidsgiver.felles.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rr.Publisher
import no.nav.helsearbeidsgiver.felles.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.rr.service.ServiceRiverStateful

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
        createAktiveOrgnrService(it, redisConnection)
    }
}

fun createAktiveOrgnrService(
    publisher: Publisher,
    redisConnection: RedisConnection,
): List<ServiceRiverStateful<AktiveOrgnrService>> =
    listOf(
        ServiceRiverStateful(
            AktiveOrgnrService(
                publisher = publisher,
                redisStore = RedisStore(redisConnection, RedisPrefix.AktiveOrgnr),
            ),
        ),
    )
