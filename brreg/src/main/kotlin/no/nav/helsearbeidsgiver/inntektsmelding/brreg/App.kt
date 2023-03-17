package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.brreg.BrregClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-brreg")

fun main() {
    val environment = setUpEnvironment()
    val isDevelopmentMode = environment.brregUrl.contains("localhost")
    RapidApplication
        .create(System.getenv())
        .createBrreg(BrregClient(environment.brregUrl), isDevelopmentMode)
        .start()
}

fun RapidsConnection.createBrreg(brregClient: BrregClient, isDevelopmentMode: Boolean): RapidsConnection {
    sikkerlogg.info("Starting VirksomhetLøser... developmentMode: $isDevelopmentMode")
    VirksomhetLøser(this, brregClient, isDevelopmentMode)
    return this
}
