package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.pdl.PdlClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-pdl")

fun main() {
    val environment = setUpEnvironment()
    val app = createApp(environment)
    app.start()
}

internal fun createApp(environment: Environment): RapidsConnection {
    logger.info("Starting RapidApplication...")

    val rapidsConnection = RapidApplication.create(environment.raw)
    val tokenProvider = OAuth2ClientConfig(environment)
    val pdl = PdlClient(environment.pdlUrl) { tokenProvider.getToken() }

    FulltNavnLøser(rapidsConnection, pdl)

    return rapidsConnection
}
