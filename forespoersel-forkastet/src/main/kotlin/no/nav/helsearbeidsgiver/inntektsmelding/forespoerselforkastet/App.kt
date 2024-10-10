package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselforkastet

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-forespoersel-forkastet".logger()

fun main() {
    logger.info("Jeg er oppe og kjører!")

    RapidApplication
        .create(System.getenv())
        .createForespoerselForkastetRiver()
        .start()

    logger.info("Bye bye, baby, bye bye!")
}

fun RapidsConnection.createForespoerselForkastetRiver(): RapidsConnection =
    also {
        logger.info("Starter ${ForespoerselForkastetRiver::class.simpleName}...")
        ForespoerselForkastetRiver(this).connect(this)
    }
