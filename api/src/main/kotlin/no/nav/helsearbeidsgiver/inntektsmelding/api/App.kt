package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.json.jsonIgnoreUnknown
import no.nav.helsearbeidsgiver.inntektsmelding.api.arbeidsgivere.ArbeidsgivereRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.InnsendingRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.trenger.TrengerRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.routeExtra
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-api")

object Routes {
    const val PREFIX = "/api/v1"

    const val ARBEIDSGIVERE = "/arbeidsgivere"
    const val INNSENDING = "/inntektsmelding"
    const val TRENGER = "/trenger"
}

fun main() {
    val env = System.getenv()
    RapidApplication.create(env)
        .also(::startServer)
        .start()
}

private fun startServer(connection: RapidsConnection) {
    embeddedServer(Netty, port = 8080) {
        apiModule(connection)
    }.start(wait = true)
}

fun Application.apiModule(connection: RapidsConnection) {
    customAuthentication()

    install(ContentNegotiation) {
        json(jsonIgnoreUnknown)
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(
                text = "Error 500: $cause",
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    HelsesjekkerRouting()

    routing {
        get("/") {
            call.respondText("helsearbeidsgiver inntektsmelding")
        }

        val redisPoller = RedisPoller()

        authenticate {
            route(Routes.PREFIX) {
                routeExtra(connection, redisPoller) {
                    ArbeidsgivereRoute()
                    TrengerRoute()
                    // Midlertidig deaktivert, lik route lagt til uten auth for enklere manuell testing
                    // InnsendingRoute()
                }
            }
        }

        route(Routes.PREFIX) {
            routeExtra(connection, redisPoller) {
                InnsendingRoute()
            }
        }
    }
}
