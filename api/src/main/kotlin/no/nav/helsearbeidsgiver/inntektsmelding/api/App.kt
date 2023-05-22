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
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.inntektsmelding.api.arbeidsgivere.ArbeidsgivereRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.InnsendingRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt.InntektRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering.KvitteringRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.trenger.TrengerRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.routeExtra
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import no.nav.helsearbeidsgiver.utils.json.jsonIgnoreUnknown
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-api")

object Routes {
    const val PREFIX = "/api/v1"

    const val ARBEIDSGIVERE = "/arbeidsgivere"
    const val INNSENDING = "/inntektsmelding"
    const val TRENGER = "/trenger"
    const val INNTEKT = "/inntekt"
    const val KVITTERING = "/kvittering"
}

fun main() {
    val env = System.getenv()
    RapidApplication.create(env)
        .also(::startServer)
        .start()
}

fun startServer(rapid: RapidsConnection) {
    embeddedServer(Netty, port = 8080) {
        apiModule(rapid)
    }.start(wait = true)
}

fun Application.apiModule(rapid: RapidsConnection) {
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

        val tilgangCache = LocalCache<Tilgang>(60.minutes, 100)

        authenticate {
            route(Routes.PREFIX) {
                routeExtra(rapid, redisPoller, tilgangCache) {
                    ArbeidsgivereRoute()
                    TrengerRoute()
                    InntektRoute()
                    InnsendingRoute()
                    KvitteringRoute()
                }
            }
        }
    }
}
