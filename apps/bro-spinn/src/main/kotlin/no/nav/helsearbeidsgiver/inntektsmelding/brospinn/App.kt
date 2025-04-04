package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.auth.AuthClient
import no.nav.helsearbeidsgiver.felles.auth.IdentityProvider
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-bro-spinn".logger()

fun main() {
    logger.info("Jeg er oppe og kjører!")

    val tokenGetter = AuthClient().tokenGetter(IdentityProvider.AZURE_AD, Env.spinnScope)
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
