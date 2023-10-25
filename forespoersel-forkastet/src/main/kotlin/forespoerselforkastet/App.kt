package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselforkastet

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-forespoersel-forkastet".logger()

fun main() {
    logger.info("Wingardium Leviosa!")

    val priProducer = PriProducer(Env.Kafka, JsonElement.serializer())

    RapidApplication
        .create(System.getenv())
        .createForespoerselForkastet(priProducer)
        .start()

    logger.info("Avada Kedavra!")
}

fun RapidsConnection.createForespoerselForkastet(priProducer: PriProducer<JsonElement>): RapidsConnection =
    apply {
        logger.info("Starting ${ForespoerselForkastetLoeser::class.simpleName}...")
        ForespoerselForkastetLoeser(this, priProducer)
    }
