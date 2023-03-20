package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.oauth2.OAuth2ClientConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerLogger: Logger = LoggerFactory.getLogger("tjenestekall")

fun main() {
    val environment = setUpEnvironment()
    RapidApplication
        .create(System.getenv())
        .createNotifikasjon(buildClient(environment), environment.linkUrl)
        .start()
}

fun RapidsConnection.createNotifikasjon(arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient, linkUrl: String): RapidsConnection {
    // Gammel
    sikkerLogger.info("Starting NotifikasjonLøser...")
    NotifikasjonLøser(this, arbeidsgiverNotifikasjonKlient, linkUrl)
    // Nye
//    sikkerLogger.info("Starting ForespørselMottattListener...")
//    ForespørselMottattListener(this)
//    sikkerLogger.info("Starting OpprettSakLøser...")
//    OpprettSakLøser(this, arbeidsgiverNotifikasjonKlient, linkUrl)
//    sikkerLogger.info("Starting OpprettOppgaveLøser...")
//    OpprettOppgaveLøser(this, arbeidsgiverNotifikasjonKlient, linkUrl)
//    sikkerLogger.info("Starting NotifikasjonInntektsmeldingMottattListener...")
//    NotifikasjonInntektsmeldingMottattListener(this)
    return this
}

fun buildClient(environment: Environment): ArbeidsgiverNotifikasjonKlient {
    val tokenProvider = OAuth2ClientConfig(environment.azureOAuthEnvironment)
    return ArbeidsgiverNotifikasjonKlient(environment.notifikasjonUrl) { tokenProvider.getToken() }
}
