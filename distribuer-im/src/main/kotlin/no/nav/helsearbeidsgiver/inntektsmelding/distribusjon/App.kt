package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-distribuer-im")
internal val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

fun main() {
    createApp(setUpEnvironment()).start()
}

internal fun createApp(environment: Environment): RapidsConnection {
    logger.info("Starting distribuer-im...")
    val rapidsConnection = RapidApplication.create(environment.raw)
    logger.info("Starting Distribuer IM Løser...")
    DistribuerIMLøser(
        rapidsConnection
    )
    return rapidsConnection
}
