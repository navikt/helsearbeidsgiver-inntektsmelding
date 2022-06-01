package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")

internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-api")

fun main() {
    val app = createApp(setUpEnvironment())
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureRouting(app)
    }.start(wait = true).also {
        app.start()
    }
}

fun createApp(environment: Environment): RapidsConnection {
    val rapidsConnection = RapidApplication.create(environment.raw)
    BehovLøser(rapidsConnection)
    return rapidsConnection
}
