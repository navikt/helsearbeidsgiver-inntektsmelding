package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-helsebro".logger()

fun main() {
    logger.info("im-helsebro er oppe og kjører!")

    val priProducer = PriProducer()

    RapidApplication
        .create(System.getenv())
        .createHelsebroRivers(priProducer)
        .start()

    logger.info("Nå dør jeg :(")
}

fun RapidsConnection.createHelsebroRivers(priProducer: PriProducer): RapidsConnection =
    also {
        logger.info("Starter ${TrengerForespoerselRiver::class.simpleName}...")
        TrengerForespoerselRiver(priProducer).connect(this)

        logger.info("Starter ${ForespoerselSvarRiver::class.simpleName}...")
        ForespoerselSvarRiver().connect(this)

        logger.info("Starter ${HentForespoerslerForVedtaksperiodeIdListeRiver::class.simpleName}...")
        HentForespoerslerForVedtaksperiodeIdListeRiver(priProducer).connect(this)

        logger.info("Starter ${VedtaksperiodeIdForespoerselSvarRiver::class.simpleName}...")
        VedtaksperiodeIdForespoerselSvarRiver().connect(this)
    }
