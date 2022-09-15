package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.helse.rapids_rivers.RapidApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("im-api")

fun main() {
    val env = System.getenv()
    RapidApplication.create(env).apply {
        val producer = InntektsmeldingRegistrertProducer(this)
        embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
            configureRouting(producer)
        }.start(wait = true)
    }.start()
}
