package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.oauth2.OAuth2ClientConfig
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

val sikkerLogger = sikkerLogger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createPdl(buildClient(setUpEnvironment()))
        .start()
}

fun RapidsConnection.createPdl(pdlClient: PdlClient): RapidsConnection {
    sikkerLogger.info("Starting FulltNavnLøser...")
    FulltNavnLøser(this, pdlClient)
    return this
}

fun buildClient(environment: Environment): PdlClient {
    val tokenProvider = OAuth2ClientConfig(environment.azureOAuthEnvironment)
    return PdlClient(environment.pdlUrl) { tokenProvider.getToken() }
}
