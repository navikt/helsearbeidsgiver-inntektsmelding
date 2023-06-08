package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

private val logger = "im-helsebro".logger()
val sikkerLogger = sikkerLogger()

fun main() {
    logger.info("im-helsebro er oppe og kjører!")

    RapidApplication
        .create(System.getenv())
        .createHelsebro(PriProducer())
        .start()

    logger.info("Nå dør jeg :(")
}

fun RapidsConnection.createHelsebro(priProducer: PriProducer): RapidsConnection {
    TrengerForespoerselLøser(this, priProducer)
    TrengerForespoerselLøser2(this, priProducer)
    ForespoerselSvarLøser(this)
    return this
}
