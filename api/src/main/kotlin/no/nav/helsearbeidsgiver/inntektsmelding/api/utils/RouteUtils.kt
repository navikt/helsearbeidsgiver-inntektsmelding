package no.nav.helsearbeidsgiver.inntektsmelding.api.utils

import io.ktor.server.routing.Route
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.utils.cache.LocalCache

data class RouteExtra(
    val route: Route,
    val connection: RapidsConnection,
    val redis: RedisPoller,
    val tilgangCache: LocalCache<Tilgang>
)

fun Route.routeExtra(
    connection: RapidsConnection,
    redis: RedisPoller,
    tilgangCache: LocalCache<Tilgang>,
    build: RouteExtra.() -> Unit
) {
    RouteExtra(this, connection, redis, tilgangCache)
        .build()
}
