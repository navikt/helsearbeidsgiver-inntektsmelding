package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-forespoersel-mottatt".logger()

fun main() {
    logger.info("Jeg er oppe og kjører!")

    val priProducer = PriProducer()

    RapidApplication
        .create(System.getenv())
        .createForespoerselMottatt(priProducer)
        .start()

    logger.info("Hasta la vista, baby!")
}

fun RapidsConnection.createForespoerselMottatt(priProducer: PriProducer): RapidsConnection =
    also {
        logger.info("Starter ${ForespoerselMottattLoeser::class.simpleName}...")
        ForespoerselMottattLoeser(this, priProducer)
    }
