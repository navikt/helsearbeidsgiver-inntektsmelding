package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")

internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-brreg")

fun main() {
    val app = createApp(setUpEnvironment())
    app.start()
}

fun createApp(environment: Environment): RapidsConnection {
    val rapidsConnection = RapidApplication.create(environment.raw)
    rapidsConnection.publish("Første melding på Rapid fra brreg modulen")
    return rapidsConnection
}
