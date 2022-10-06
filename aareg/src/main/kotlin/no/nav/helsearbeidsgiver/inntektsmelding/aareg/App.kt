package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.aareg.AaregClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-aareg")

fun main() {
    val environment = setUpEnvironment()
    val app = createApp(environment)
    app.start()
}

internal fun createApp(environment: Environment): RapidsConnection {
    logger.info("Starting RapidApplication...")
    val rapidsConnection = RapidApplication.create(environment.raw)
    logger.info("Starting...")
    val tokenProvider = OAuth2ClientConfig(environment)
    val aaregClient = AaregClient(environment.aaregUrl) { tokenProvider.getToken() }
    ArbeidsforholdLøser(rapidsConnection, aaregClient)
    return rapidsConnection
}
