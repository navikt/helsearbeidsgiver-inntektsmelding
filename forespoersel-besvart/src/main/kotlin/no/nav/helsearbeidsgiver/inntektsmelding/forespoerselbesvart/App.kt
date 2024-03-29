package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-forespoersel-besvart".logger()

fun main() {
    logger.info("Jeg er oppe og kjører!")

    val priProducer = PriProducer()

    RapidApplication
        .create(System.getenv())
        .createForespoerselBesvartFraSimba()
        .createForespoerselBesvartFraSpleis(priProducer)
        .start()

    logger.info("Bye bye, baby, bye bye!")
}

fun RapidsConnection.createForespoerselBesvartFraSimba(): RapidsConnection =
    also {
        logger.info("Starter ${ForespoerselBesvartFraSimbaLoeser::class.simpleName}...")
        ForespoerselBesvartFraSimbaLoeser(this)
    }

fun RapidsConnection.createForespoerselBesvartFraSpleis(priProducer: PriProducer): RapidsConnection =
    also {
        logger.info("Starter ${ForespoerselBesvartFraSpleisLoeser::class.simpleName}...")
        ForespoerselBesvartFraSpleisLoeser(this, priProducer)
    }
