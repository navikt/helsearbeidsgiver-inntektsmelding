package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-forespoersel-marker-besvart".logger()

fun main() {
    logger.info("Up, up and away!")

    val priProducer = PriProducer()

    RapidApplication
        .create(System.getenv())
        .createMarkerForespoerselBesvart(priProducer)
        .start()

    logger.info("Bye bye, baby, bye bye!")
}

fun RapidsConnection.createMarkerForespoerselBesvart(priProducer: PriProducer): RapidsConnection =
    also {
        logger.info("Starter ${MarkerForespoerselBesvartRiver::class.simpleName}...")
        MarkerForespoerselBesvartRiver(priProducer).connect(this)
    }
