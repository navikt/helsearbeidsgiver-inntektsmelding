package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.oauth2.OAuth2ClientConfig
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-inntekt".logger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createInntekt(createInntektKlient())
        .start()
}

fun RapidsConnection.createInntekt(inntektKlient: InntektKlient): RapidsConnection =
    also {
        logger.info("Starter InntektLoeser...")
        InntektLoeser(this, inntektKlient)
    }

fun createInntektKlient(): InntektKlient {
    val tokenProvider = OAuth2ClientConfig(Env.azureOAuthEnvironment)
    return InntektKlient(Env.inntektUrl, tokenProvider::getToken)
}
