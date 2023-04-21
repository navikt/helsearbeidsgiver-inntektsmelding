package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.oauth2.OAuth2ClientConfig
import no.nav.helsearbeidsgiver.pdl.PdlClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-pdl")

fun main() {
    RapidApplication
        .create(System.getenv())
        .createPdl(buildClient(setUpEnvironment()))
        .start()
}

fun RapidsConnection.createPdl(pdlClient: PdlClient): RapidsConnection {
    sikkerlogg.info("Starting FulltNavnLøser...")
    FulltNavnLøser(this, pdlClient, LocalCache<PersonDato>(60.minutes, 100))
    return this
}

fun buildClient(environment: Environment): PdlClient {
    val tokenProvider = OAuth2ClientConfig(environment.azureOAuthEnvironment)
    return PdlClient(environment.pdlUrl) { tokenProvider.getToken() }
}
