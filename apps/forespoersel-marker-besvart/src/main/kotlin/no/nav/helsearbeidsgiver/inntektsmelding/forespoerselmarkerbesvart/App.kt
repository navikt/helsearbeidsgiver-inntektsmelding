package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.kafka.Producer
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-forespoersel-marker-besvart".logger()

fun main() {
    logger.info("Up, up and away!")

    val producer = Producer()

    RapidApplication
        .create(System.getenv())
        .createMarkerForespoerselBesvart(producer)
        .start()

    logger.info("Bye bye, baby, bye bye!")
}

fun RapidsConnection.createMarkerForespoerselBesvart(producer: Producer): RapidsConnection =
    also {
        logger.info("Starter ${MarkerForespoerselBesvartRiver::class.simpleName}...")
        MarkerForespoerselBesvartRiver(producer).connect(this)

        logger.info("Starter ${ForespoerselMottattRiver::class.simpleName}...")
        ForespoerselMottattRiver().connect(this)

        logger.info("Starter ${ForespoerselBesvartRiver::class.simpleName}...")
        ForespoerselBesvartRiver().connect(this)

        logger.info("Starter ${ForespoerselKastetTilInfotrygdRiver::class.simpleName}...")
        ForespoerselKastetTilInfotrygdRiver().connect(this)

        logger.info("Starter ${ForespoerselForkastetRiver::class.simpleName}...")
        ForespoerselForkastetRiver().connect(this)
    }
