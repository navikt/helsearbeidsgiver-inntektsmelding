package no.nav.helsearbeidsgiver.inntektsmelding.soeknadservice

import no.nav.hag.simba.utils.rr.Publisher
import no.nav.hag.simba.utils.rr.river.ObjectRiver
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
        createHentSoeknaderService(it, redisConnection)
    }
}

fun createHentSoeknaderService(
    publisher: Publisher,
    redisConnection: RedisConnection,
): List<ServiceRiverStateless> =
    listOf(
        ServiceRiverStateless(
            HentSoeknaderService(
                publisher = publisher,
                redisStore = RedisStore(redisConnection, RedisPrefix.HentSoeknader),
            ),
        ),
    )
