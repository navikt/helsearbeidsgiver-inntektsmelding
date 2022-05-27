package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.server.application.log
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        log.info("Starting....")
        configureRouting()
    }.start(wait = true)
}
