package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import no.nav.helse.rapids_rivers.RapidApplication
import org.slf4j.LoggerFactory

val logger = LoggerFactory.getLogger("helsebro-main")
val loggerSikker = LoggerFactory.getLogger("tjenestekall")

/*
Denne appen skal snakke med helsearbeidsgiver-bro-sykepenger etterhvert.
*/
fun main() {
    logger.info("im-helsebro er oppe og kjører!")

    RapidApplication.create(System.getenv())
        .also {
            HelsebroLøser(it, PriProducer())
        }
        .start()

    logger.info("Nå dør jeg :(")
}
