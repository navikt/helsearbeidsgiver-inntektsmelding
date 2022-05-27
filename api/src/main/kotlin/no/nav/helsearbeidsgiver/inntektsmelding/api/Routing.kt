package no.nav.helsearbeidsgiver.inntektsmelding.api

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
        get("isalive") {
            call.respondText("I'm alive")
        }
        get("isready") {
            call.respondText("I'm ready")
        }
        get("/isalive") {
            call.respondText("I'm alive")
        }
        get("/isready") {
            call.respondText("I'm ready")
        }
        get("/internal/isalive") {
            call.respondText("I'm alive")
        }
        get("/internal/isready") {
            call.respondText("I'm ready")
        }
    }
}
