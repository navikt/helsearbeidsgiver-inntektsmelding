package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselinfotrygd

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-forespoersel-infotrygd".logger()

fun main() {
    logger.info("Jeg er oppe og kjører!")

    RapidApplication
        .create(System.getenv())
        .createForespoerselKastetTilInfotrygdRiver()
        .start()

    logger.info("Bye bye, baby, bye bye!")
}

fun RapidsConnection.createForespoerselKastetTilInfotrygdRiver(): RapidsConnection =
    also {
        logger.info("Starter ${ForespoerselKastetTilInfotrygdRiver::class.simpleName}...")
        ForespoerselKastetTilInfotrygdRiver().connect(this)
    }
