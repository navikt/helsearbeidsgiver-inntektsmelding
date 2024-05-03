package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.tokenprovider.oauth2ClientCredentialsTokenGetter
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-aareg".logger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createAareg(buildClient(setUpEnvironment()))
        .start()
}

fun RapidsConnection.createAareg(aaregClient: AaregClient): RapidsConnection =
    also {
        logger.info("Starter ${ArbeidsforholdLoeser::class.simpleName}...")
        ArbeidsforholdLoeser(this, aaregClient)
    }

fun buildClient(environment: Environment): AaregClient {
    val tokenGetter = oauth2ClientCredentialsTokenGetter(environment.oauth2Environment)
    return AaregClient(url = environment.aaregUrl, getAccessToken = tokenGetter)
}
