package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.log.logger
import no.nav.helsearbeidsgiver.felles.log.loggerSikker

val logger = "im-helsebro".logger()
val loggerSikker = loggerSikker()

fun main() {
    logger.info("im-helsebro er oppe og kjører!")

    RapidApplication.create(System.getenv())
        .also {
            TrengerForespoerselLøser(it, PriProducer())
            ForespoerselSvarLøser(it)
        }
        .start()

    logger.info("Nå dør jeg :(")
}
