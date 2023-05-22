package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

val logger = "im-forespoersel-mottatt".logger()
val loggerSikker = sikkerLogger()

fun main() {
    logger.info("Jeg er oppe og kjører!")
    RapidApplication
        .create(System.getenv())
        .createForespoerselMottatt()
        .start()
    logger.info("Hasta la vista, baby!")
}

fun RapidsConnection.createForespoerselMottatt(): RapidsConnection {
    logger.info("Starting ForespoerselMottattLøser...")
    ForespoerselMottattLøser(this)
    return this
}
