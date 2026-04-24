package no.nav.helsearbeidsgiver.inntektsmelding.faisuservice

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
        createHentArbeidsforholdServices(it, redisConnection)
    }
}

fun createHentArbeidsforholdServices(
    publisher: Publisher,
    redisConnection: RedisConnection,
): List<ServiceRiverStateless> =
    listOf(
        ServiceRiverStateless(
            HentArbeidsforholdService(
                publisher = publisher,
                redisStore = RedisStore(redisConnection, RedisPrefix.HentArbeidsforhold),
            ),
        ),
        ServiceRiverStateless(
            HentArbeidsforholdSelvbestemtService(
                publisher = publisher,
                redisStore = RedisStore(redisConnection, RedisPrefix.HentArbeidsforholdSelvbestemt),
            ),
        ),
    )
