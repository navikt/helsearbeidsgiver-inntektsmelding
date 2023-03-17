package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.log.logger
import no.nav.helsearbeidsgiver.felles.log.loggerSikker

val logger = "im-helsebro".logger()
val loggerSikker = loggerSikker()

fun main() {
    logger.info("im-helsebro er oppe og kjører!")

    RapidApplication
        .create(System.getenv())
        .createHelsebro()
        .start()

    logger.info("Nå dør jeg :(")
}

fun RapidsConnection.createHelsebro(): RapidsConnection {
    loggerSikker.info("Starting TrengerForespoerselLøser...")
    TrengerForespoerselLøser(this, PriProducer())
    loggerSikker.info("Starting ForespoerselSvarLøser...")
    ForespoerselSvarLøser(this)
    return this
}
