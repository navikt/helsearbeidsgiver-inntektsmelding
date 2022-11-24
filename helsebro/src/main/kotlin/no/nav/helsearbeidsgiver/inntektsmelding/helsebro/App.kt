package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("helsebro-main")

/*
Denne appen skal snakke med helsearbeidsgiver-bro-sykepenger.
*/
fun main() {
    logger.info("im-helsebro er oppe og kjører!")

    embeddedServer(Netty, port = 8080) {
        routing {
            get("isalive") {
                call.respondText("I'm alive")
            }
            get("isready") {
                call.respondText("I'm ready")
            }
        }
    }.start()

    logger.info("Nå dør jeg :(")
}
