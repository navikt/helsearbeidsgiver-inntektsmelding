package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.TrengerForespoersel
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

private val logger = "im-helsebro".logger()
val sikkerLogger = sikkerLogger()

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
    apply {
        TrengerForespoerselLøser(this, priProducer)
        ForespoerselSvarLøser(this)
    }
