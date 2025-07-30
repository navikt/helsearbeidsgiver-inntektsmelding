package no.nav.helsearbeidsgiver.inntektsmelding.inntektselvbestemtservice

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
        createInntektSelvbestemtService(it, redisConnection)
    }
}

fun createInntektSelvbestemtService(
    publisher: Publisher,
    redisConnection: RedisConnection,
): List<ServiceRiverStateless> =
    listOf(
        ServiceRiverStateless(
            InntektSelvbestemtService(
                publisher = publisher,
                redisStore = RedisStore(redisConnection, RedisPrefix.InntektSelvbestemt),
            ),
        ),
    )
