package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.log.logger
import no.nav.helsearbeidsgiver.felles.log.loggerSikker

val logger = "im-forespoersel-mottatt".logger()
val loggerSikker = loggerSikker()

fun main() {
    logger.info("Jeg er oppe og kjører!")

    RapidApplication.create(System.getenv()).also {
        ForespoerselMottattLøser(it)
        it.start()
    }

    logger.info("Hasta la vista, baby!")
}
