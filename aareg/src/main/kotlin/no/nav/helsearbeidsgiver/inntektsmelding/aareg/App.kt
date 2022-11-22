package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.felles.oauth2.OAuth2ClientConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-aareg")

fun main() {
    createApp(setUpEnvironment()).start()
}

internal fun createApp(environment: Environment): RapidsConnection {
    logger.info("Starting RapidApplication...")
    val rapidsConnection = RapidApplication.create(environment.raw)
    logger.info("Starter løser...")
    val tokenProvider = OAuth2ClientConfig(environment.azureOAuthEnvironment)
    val aaregClient = AaregClient(environment.aaregUrl, tokenProvider::getToken)
    ArbeidsforholdLøser(rapidsConnection, aaregClient)
    logger.info("Løser klar...")
    return rapidsConnection
}
