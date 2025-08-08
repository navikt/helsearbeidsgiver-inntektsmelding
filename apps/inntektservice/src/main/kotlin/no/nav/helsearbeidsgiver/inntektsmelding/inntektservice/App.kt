package no.nav.helsearbeidsgiver.inntektsmelding.inntektservice

import no.nav.helsearbeidsgiver.felles.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rr.Publisher
import no.nav.helsearbeidsgiver.felles.rr.river.ObjectRiver
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
        createInntektService(it, redisConnection)
    }
}

fun createInntektService(
    publisher: Publisher,
    redisConnection: RedisConnection,
): List<ServiceRiverStateless> =
    listOf(
        ServiceRiverStateless(
            InntektService(
                publisher = publisher,
                redisStore = RedisStore(redisConnection, RedisPrefix.Inntekt),
            ),
        ),
    )
