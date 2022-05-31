package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon

fun Application.configureRouting() {
    install(ContentNegotiation) {
        jackson()
    }
    routing {
        get("/") {
            call.respondText("Hello inntektsmelding")
        }
        route ("/api/v1"){
            route("/arbeidsgivere"){
                get(""){
                    val ao = AltinnOrganisasjon("Norge as", "","","","","", "")
                    val list = listOf<AltinnOrganisasjon>(ao)
                    call.respond(list)
                }
            }
            route("/login-expiry") {
                get {
                    call.respond(HttpStatusCode.OK, "2022-05-31")
                }
            }
        }
    }
    routing {
        get("isalive") {
            call.respondText("I'm alive")
        }
        get("isready") {
            call.respondText("I'm ready")
        }
    }
}
