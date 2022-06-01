package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.inntektsmelding.api.mock.mockOrganisasjoner

fun Application.configureRouting(rapidsConnection: RapidsConnection) {
    rapidsConnection.start()
    install(ContentNegotiation) {
        jackson()
    }
    routing {
        get("/") {
            call.respondText("Hello inntektsmelding")
        }
        route ("/api/v1"){
            route("/arbeidsgivere"){
                get {
                    call.respond(mockOrganisasjoner())
                }
            }
            route("/login-expiry") {
                get {
                    call.respond(HttpStatusCode.OK, "2099-05-31")
                }
            }
            route("/inntektsmelding") {
                post {
                    val request = call.receive<InntektsmeldingRequest>()
                    rapidsConnection.publish("Ny rapid på køen")
                    request.validate()
                    call.respond(HttpStatusCode.Created, "Ok")
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
