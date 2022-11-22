package no.nav.helsearbeidsgiver.inntektsmelding.api.utils

import io.ktor.server.routing.Route
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller

data class RouteExtra(
    val route: Route,
    val connection: RapidsConnection,
    val redis: RedisPoller
)

fun Route.routeExtra(
    connection: RapidsConnection,
    redis: RedisPoller,
    build: RouteExtra.() -> Unit
) {
    RouteExtra(this, connection, redis)
        .build()
}
