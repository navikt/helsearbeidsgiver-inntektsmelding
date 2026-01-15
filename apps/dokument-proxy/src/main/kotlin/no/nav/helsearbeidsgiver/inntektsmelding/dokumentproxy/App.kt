package no.nav.helsearbeidsgiver.inntektsmelding.dokumentproxy

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.engine.addShutdownHook
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.hag.simba.utils.auth.AuthClient
import no.nav.hag.simba.utils.auth.IdentityProvider
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

val logger = "helsearbeidsgiver-im-dokument-proxy".logger()
val sikkerLogger = sikkerLogger()

object Routes {
    const val PREFIX = "/api/v1"
}

fun main() {
    embeddedServer(
        factory = Netty,
        port = 8080,
        module = { apiModule(AuthClient()) },
    ).apply {
        addShutdownHook {
        }
    }.start(wait = true)
}

fun Application.apiModule(authClient: AuthClient) {
    install(ContentNegotiation) {
        json(jsonConfig)
    }
    install(Authentication) {
        texas {
            client = authClient
            // ingress = config.ingress
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->

            call.respondText(
                status = HttpStatusCode.InternalServerError,
                text = "error",
            )
        }
    }

    helsesjekkerRouting()

    routing {
        get("/") {
            call.respondText("dokument proxy")
        }

        authenticate {
            route(Routes.PREFIX) {
                get("pdf") {
                    logger.info("pdf request received")
                    val principal = call.principal<TexasPrincipal>()
                    if (principal == null) {
                        call.respond(HttpStatusCode.Unauthorized, "missing principal")
                        return@get
                    }
                    val tokenxToken = authClient.exchange(IdentityProvider.TOKEN_X, Env.lpsApiScope,principal.token)
                    call.respond("")
                }
            }
        }
    }
}
