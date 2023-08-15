package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-forespoersel-besvart".logger()

fun main() {
    logger.info("Jeg er oppe og kjører!")

    val priProducer = PriProducer(Env.Kafka, JsonElement.serializer())

    RapidApplication
        .create(System.getenv())
        .createForespoerselBesvart(priProducer)
        .start()

    logger.info("Bye bye, baby, bye bye!")
}

fun RapidsConnection.createForespoerselBesvart(priProducer: PriProducer<JsonElement>): RapidsConnection =
    apply {
        logger.info("Starting ${ForespoerselBesvartLoeser::class.simpleName}...")
        ForespoerselBesvartLoeser(this, priProducer)
    }
