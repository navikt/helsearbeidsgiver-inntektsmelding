package no.nav.helsearbeidsgiver.helsebro

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
