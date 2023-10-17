package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.TrengerForespoersel
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-helsebro".logger()

fun main() {
    logger.info("im-helsebro er oppe og kjører!")

    val priProducer = PriProducer(Env.Kafka, TrengerForespoersel.serializer())

    RapidApplication
        .create(System.getenv())
        .createHelsebro(priProducer)
        .start()

    logger.info("Nå dør jeg :(")
}

fun RapidsConnection.createHelsebro(priProducer: PriProducer<TrengerForespoersel>): RapidsConnection =
    also {
        logger.info("Starter ${TrengerForespoerselLoeser::class.simpleName}...")
        TrengerForespoerselLoeser(this, priProducer)

        logger.info("Starter ${ForespoerselSvarLoeser::class.simpleName}...")
        ForespoerselSvarLoeser(this)
    }
