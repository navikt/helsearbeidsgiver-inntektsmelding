package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemtlagreimservice

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
        createLagreSelvbestemtImService(it, redisConnection)
    }
}

fun createLagreSelvbestemtImService(
    publisher: Publisher,
    redisConnection: RedisConnection,
): List<ServiceRiverStateful<LagreSelvbestemtImService>> =
    listOf(
        ServiceRiverStateful(
            LagreSelvbestemtImService(
                publisher = publisher,
                redisStore = RedisStore(redisConnection, RedisPrefix.LagreSelvbestemtIm),
            ),
        ),
    )
