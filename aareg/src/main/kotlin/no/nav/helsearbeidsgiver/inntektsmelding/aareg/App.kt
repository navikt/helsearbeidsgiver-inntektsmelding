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
        .createAaregRiver(buildClient())
        .start()
}

fun RapidsConnection.createAaregRiver(aaregClient: AaregClient): RapidsConnection =
    also {
        logger.info("Starter ${ArbeidsforholdLoeser::class.simpleName}...")
        ArbeidsforholdLoeser(this, aaregClient)

        logger.info("Starter ${HentArbeidsforholdRiver::class.simpleName}...")
        HentArbeidsforholdRiver(aaregClient).connect(this)
    }

private fun buildClient(): AaregClient {
    val tokenGetter = oauth2ClientCredentialsTokenGetter(Env.oauth2Environment)
    return AaregClient(url = Env.aaregUrl, getAccessToken = tokenGetter)
}
