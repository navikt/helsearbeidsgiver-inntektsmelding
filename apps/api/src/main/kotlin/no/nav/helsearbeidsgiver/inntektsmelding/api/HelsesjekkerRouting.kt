package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.helsesjekkerRouting() {
    routing {
        get("isalive") {
            call.respondText("I'm alive")
        }
        get("isready") {
            call.respondText("I'm ready")
        }
    }
}
