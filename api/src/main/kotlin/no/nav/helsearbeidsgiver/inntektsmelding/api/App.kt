package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.inntektsmelding.api.arbeidsgiver.arbeidsgiverRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.innsendingRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt.preutfyltRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.contentNegotiation
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.routingExtra
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-api")

fun main() {
    val env = System.getenv()

    RapidApplication.create(env)
        .also(::startServer)
        .start()
}

private fun startServer(connection: RapidsConnection) {
    embeddedServer(Netty, port = 8080) {
        contentNegotiation()

        helsesjekkerRouting()

        routingExtra(connection, RedisPoller()) {
            routing.get("/") {
                call.respondText("helsearbeidsgiver inntektsmelding")
            }

            routeExtra("/api/v1") {
                arbeidsgiverRoute()
                innsendingRoute()
                preutfyltRoute()
            }
        }
    }.start(wait = true)
}
