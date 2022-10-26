package no.nav.helsearbeidsgiver.inntektsmelding.api.utils

import io.ktor.server.application.Application
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller

fun Application.routingExtra(
    connection: RapidsConnection,
    redis: RedisPoller,
    configuration: RoutingExtra.() -> Unit
) {
    routing {
        RoutingExtra(this, connection, redis)
            .configuration()
    }
}

data class RoutingExtra(
    val routing: Routing,
    val connection: RapidsConnection,
    val redis: RedisPoller
) {
    fun routeExtra(
        path: String,
        build: RouteExtra.() -> Unit
    ): Route =
        routing.route(path) {
            RouteExtra(this, connection, redis)
                .build()
        }
}

data class RouteExtra(
    val route: Route,
    val connection: RapidsConnection,
    val redis: RedisPoller
)
