package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-forespoersel-besvart".logger()

fun main() {
    logger.info("Jeg er oppe og kjører!")

    RapidApplication
        .create(System.getenv())
        .createForespoerselBesvartRivers()
        .start()

    logger.info("Bye bye, baby, bye bye!")
}

fun RapidsConnection.createForespoerselBesvartRivers(): RapidsConnection =
    also {
        logger.info("Starter ${ForespoerselBesvartFraSimbaRiver::class.simpleName}...")
        ForespoerselBesvartFraSimbaRiver().connect(this)

        logger.info("Starter ${ForespoerselBesvartFraSpleisRiver::class.simpleName}...")
        ForespoerselBesvartFraSpleisRiver(this).connect(this)
    }
