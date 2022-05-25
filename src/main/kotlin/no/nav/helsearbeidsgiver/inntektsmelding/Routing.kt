package no.nav.helsearbeidsgiver.inntektsmelding

import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    install(ContentNegotiation) {
        jackson()
    }
    routing {
        get("/") {
            call.respondText("Hello inntektsmelding")
        }
    }
    routing {
        get("/is-alive") {
            call.respondText("I'm alive")
        }
    }
    routing {
        get("/is-ready") {
            call.respondText("I'm ready")
        }
    }
}
