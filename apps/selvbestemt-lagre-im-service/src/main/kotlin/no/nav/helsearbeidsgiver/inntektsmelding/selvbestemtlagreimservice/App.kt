package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemtlagreimservice

import no.nav.hag.simba.utils.rr.Publisher
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.hag.simba.utils.rr.service.ServiceRiverStateful
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
