package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-forespoersel-besvart".logger()

fun main() {
    logger.info("Jeg er oppe og kjører!")

    RapidApplication
        .create(System.getenv())
        .createForespoerselBesvartRiver()
        .start()

    logger.info("Bye bye, baby, bye bye!")
}

fun RapidsConnection.createForespoerselBesvartRiver(): RapidsConnection =
    also {
        logger.info("Starter ${ForespoerselBesvartRiver::class.simpleName}...")
        ForespoerselBesvartRiver(this).connect(this)
    }
