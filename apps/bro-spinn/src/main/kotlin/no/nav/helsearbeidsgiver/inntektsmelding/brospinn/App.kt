package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.tokenprovider.oauth2ClientCredentialsTokenGetter
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-bro-spinn".logger()

fun main() {
    logger.info("Jeg er oppe og kjører!")

    val tokenGetter = oauth2ClientCredentialsTokenGetter(Env.oauth2Environment)
    val spinnKlient = SpinnKlient(Env.spinnUrl, tokenGetter)

    RapidApplication
        .create(System.getenv())
        .createHentEksternImRiver(spinnKlient)
        .start()

    logger.info("Bye bye, baby, bye bye!")
}

fun RapidsConnection.createHentEksternImRiver(spinnKlient: SpinnKlient): RapidsConnection =
    also {
        logger.info("Starter ${HentEksternImRiver::class.simpleName}...")
        HentEksternImRiver(spinnKlient).connect(this)
    }
