package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")

internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-api")

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureRouting()
    }.start(wait = true)
}
