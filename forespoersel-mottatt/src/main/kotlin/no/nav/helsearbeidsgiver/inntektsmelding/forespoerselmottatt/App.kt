package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-forespoersel-mottatt".logger()

fun main() {
    logger.info("Jeg er oppe og kjører!")
    RapidApplication
        .create(System.getenv())
        .createForespoerselMottatt()
        .start()
    logger.info("Hasta la vista, baby!")
}

fun RapidsConnection.createForespoerselMottatt(): RapidsConnection =
    apply {
        logger.info("Starting ${ForespoerselMottattLøser::class.simpleName}...")
        ForespoerselMottattLøser(this)
    }
