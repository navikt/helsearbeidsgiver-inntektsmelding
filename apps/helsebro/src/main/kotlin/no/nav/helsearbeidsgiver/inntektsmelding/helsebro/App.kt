package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.kafka.Producer
import no.nav.helsearbeidsgiver.felles.kafka.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.onShutdown
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-helsebro".logger()

fun main() {
    logger.info("im-helsebro er oppe og kjører!")

    val producer = Producer(Pri.TOPIC)

    RapidApplication
        .create(System.getenv())
        .createHelsebroRivers(producer)
        .onShutdown {
            producer.close()
        }.start()

    logger.info("Nå dør jeg :(")
}

fun RapidsConnection.createHelsebroRivers(producer: Producer): RapidsConnection =
    also {
        logger.info("Starter ${TrengerForespoerselRiver::class.simpleName}...")
        TrengerForespoerselRiver(producer).connect(this)

        logger.info("Starter ${ForespoerselSvarRiver::class.simpleName}...")
        ForespoerselSvarRiver().connect(this)

        logger.info("Starter ${HentForespoerslerForVedtaksperiodeIdListeRiver::class.simpleName}...")
        HentForespoerslerForVedtaksperiodeIdListeRiver(producer).connect(this)

        logger.info("Starter ${VedtaksperiodeIdForespoerselSvarRiver::class.simpleName}...")
        VedtaksperiodeIdForespoerselSvarRiver().connect(this)
    }
