package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.felles.oauth2.OAuth2ClientConfig
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

val sikkerLogger = sikkerLogger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createAareg(buildClient(setUpEnvironment()))
        .start()
}

fun RapidsConnection.createAareg(aaregClient: AaregClient): RapidsConnection {
    sikkerLogger.info("Starter ArbeidsforholdLøser...")
    ArbeidsforholdLøser(this, aaregClient)
    return this
}

fun buildClient(environment: Environment): AaregClient {
    val tokenProvider = OAuth2ClientConfig(environment.azureOAuthEnvironment)
    return AaregClient(url = environment.aaregUrl, getAccessToken = tokenProvider::getToken)
}
