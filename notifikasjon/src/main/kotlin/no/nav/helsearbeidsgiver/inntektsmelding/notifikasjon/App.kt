package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.oauth2.OAuth2ClientConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-notifikasjon")

fun main() {
    createApp(setUpEnvironment()).start()
}

internal fun createApp(environment: Environment): RapidsConnection {
    logger.info("Starting RapidApplication...")
    val rapidsConnection = RapidApplication.create(environment.raw)
    logger.info("Starting...")
    NotifikasjonLøser(rapidsConnection, buildClient(environment), environment.linkUrl)
    NotifikasjonInntektsmeldingMottattListener(rapidsConnection)
    return rapidsConnection
}

fun buildClient(environment: Environment): ArbeidsgiverNotifikasjonKlient {
    val tokenProvider = OAuth2ClientConfig(environment.azureOAuthEnvironment)
    return ArbeidsgiverNotifikasjonKlient(environment.notifikasjonUrl) { tokenProvider.getToken() }
}
