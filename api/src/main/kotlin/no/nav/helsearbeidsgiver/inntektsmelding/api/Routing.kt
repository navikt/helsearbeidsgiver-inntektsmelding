package no.nav.helsearbeidsgiver.inntektsmelding.api

import com.typesafe.config.ConfigFactory
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.helsearbeidsgiver.inntektsmelding.api.mock.mockOrganisasjoner
import no.nav.security.token.support.v2.tokenValidationSupport

fun Application.configureRouting(producer: InntektsmeldingRegistrertProducer) {
    install(ContentNegotiation) {
        jackson()
    }
    install(Authentication) {
        val config = HoconApplicationConfig(ConfigFactory.load())
        tokenValidationSupport(config = config)
    }
    routing {
        get("/") {
            call.respondText("Hello inntektsmelding")
        }
        authenticate {
            route("/api/v1") {
                route("/arbeidsgivere") {
                    get {
                        call.respond(mockOrganisasjoner())
                    }
                }
                route("/inntektsmelding") {
                    post {
                        val request = call.receive<InntektsmeldingRequest>()
                        logger.info("Mottok inntektsmelding $request")
                        request.validate()
                        logger.info("Publiser til Rapid")
                        producer.publish(request)
                        call.respond(HttpStatusCode.Created, "Ok")
                    }
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
