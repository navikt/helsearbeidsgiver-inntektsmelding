package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.oauth2.OAuth2ClientConfig
import no.nav.helsearbeidsgiver.pdl.Behandlingsgrunnlag
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-pdl".logger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createPdl(buildClient(setUpEnvironment()))
        .start()
}

fun RapidsConnection.createPdl(pdlClient: PdlClient): RapidsConnection =
    also {
        logger.info("Starter ${FulltNavnLoeser::class.simpleName}...")
        FulltNavnLoeser(this, pdlClient)

        logger.info("Starter ${HentPersonerRiver::class.simpleName}...")
        HentPersonerRiver(pdlClient).connect(this)
    }

fun buildClient(environment: Environment): PdlClient {
    val tokenProvider = OAuth2ClientConfig(environment.azureOAuthEnvironment)
    return PdlClient(environment.pdlUrl, Behandlingsgrunnlag.INNTEKTSMELDING) { tokenProvider.getToken() }
}
