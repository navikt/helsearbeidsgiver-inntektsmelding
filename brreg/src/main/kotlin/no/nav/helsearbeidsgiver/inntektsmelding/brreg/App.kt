package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

val sikkerLogger = sikkerLogger()

fun main() {
    val environment = setUpEnvironment()
    val isDevelopmentMode = environment.brregUrl.contains("localhost")
    RapidApplication
        .create(System.getenv())
        .createBrreg(BrregClient(environment.brregUrl), isDevelopmentMode)
        .start()
}

fun RapidsConnection.createBrreg(brregClient: BrregClient, isDevelopmentMode: Boolean): RapidsConnection {
    sikkerLogger.info("Starting VirksomhetLøser... developmentMode: $isDevelopmentMode")
    VirksomhetLøser(this, brregClient, isDevelopmentMode)
    return this
}
