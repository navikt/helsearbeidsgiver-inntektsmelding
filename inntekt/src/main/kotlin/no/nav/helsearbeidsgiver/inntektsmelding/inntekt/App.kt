package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.tokenprovider.oauth2ClientCredentialsTokenGetter
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
        logger.info("Starter ${InntektLoeser::class.simpleName}...")
        InntektLoeser(this, inntektKlient)

        logger.info("Starter ${HentInntektRiver::class.simpleName}...")
        HentInntektRiver(inntektKlient).connect(this)
    }

fun createInntektKlient(): InntektKlient {
    val tokenGetter = oauth2ClientCredentialsTokenGetter(Env.oauth2Environment)
    return InntektKlient(Env.inntektUrl, tokenGetter)
}
