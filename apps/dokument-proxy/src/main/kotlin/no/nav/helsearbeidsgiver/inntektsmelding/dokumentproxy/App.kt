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
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.hag.simba.utils.auth.AuthClient
import no.nav.hag.simba.utils.auth.IdentityProvider
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

val logger = "helsearbeidsgiver-im-dokument-proxy".logger()
val sikkerLogger = sikkerLogger()

object Routes {
    const val PREFIX = "/api/v1"
}

fun main() {
    embeddedServer(
        factory = Netty,
        port = 8080,
        module = { apiModule(AuthClient(), PdfClient()) },
    ).apply {
        addShutdownHook {
        }
    }.start(wait = true)
}

fun Application.apiModule(
    authClient: AuthClient,
    pdfClient: PdfClient = PdfClient(),
) {
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
            logger.info("Unhandled exception caught in status pages")
            sikkerLogger.error("Unhandled exception caught in status pages", cause)

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
                get("test") {
                    call.respondText("test ok")
                }
                get("sykmelding/{uuid}.pdf") {
                    val uuidParam = call.parameters["uuid"]
                    if (uuidParam == null) {
                        call.respond(HttpStatusCode.BadRequest, "mangler sykmelding id")
                        return@get
                    }

                    val uuid =
                        try {
                            UUID.fromString(uuidParam)
                        } catch (_e: IllegalArgumentException) {
                            call.respond(HttpStatusCode.BadRequest, "ugyldig sykmelding id")
                            return@get
                        }

                    logger.info("pdf request received for uuid: $uuid")
                    try {
                        val principal = call.principal<TexasPrincipal>()
                        if (principal == null) {
                            call.respond(HttpStatusCode.Unauthorized, "mangler gyldig token")
                            return@get
                        }
                        val tokenxToken = authClient.exchange(IdentityProvider.TOKEN_X, Env.lpsApiTarget, principal.token)

                        when (val pdfResponse = pdfClient.genererPDF(uuid, tokenxToken.accessToken)) {
                            is PdfResponse.Success -> {
                                call.response.headers.append("Content-Type", "application/pdf")
                                call.response.headers.append("Content-Disposition", "inline; filename=\"sykmelding-$uuid.pdf\"")
                                call.respondBytes(
                                    bytes = pdfResponse.pdf,
                                    contentType = io.ktor.http.ContentType.Application.Pdf,
                                    status = HttpStatusCode.OK,
                                )
                            }

                            is PdfResponse.Unauthorized -> {
                                logger.warn("Unauthorized/Forbidden when fetching PDF for uuid: $uuid, status: ${pdfResponse.status}")
                                call.respond(pdfResponse.status, "Ikke autorisert til å hente PDF")
                            }

                            is PdfResponse.Failure -> {
                                logger.error("Failed to fetch PDF for uuid: $uuid, status: ${pdfResponse.status}")
                                call.respond(pdfResponse.status, "Kunne ikke hente PDF")
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Error while fetching PDF for uuid: $uuid")
                        sikkerLogger.error("Error while fetching PDF for uuid: $uuid", e)
                        call.respond(HttpStatusCode.InternalServerError, "Feil ved henting av PDF")
                    }
                }
            }
        }
    }
}
