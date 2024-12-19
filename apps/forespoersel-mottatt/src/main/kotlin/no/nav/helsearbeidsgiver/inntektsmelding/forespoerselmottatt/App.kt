package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-forespoersel-mottatt".logger()

fun main() {
    logger.info("Jeg er oppe og kjører!")

    RapidApplication
        .create(System.getenv())
        .createForespoerselMottattRiver()
        .start()

    logger.info("Hasta la vista, baby!")
}

fun RapidsConnection.createForespoerselMottattRiver(): RapidsConnection =
    also {
        logger.info("Starter ${ForespoerselMottattRiver::class.simpleName}...")
        ForespoerselMottattRiver().connect(this)
    }
