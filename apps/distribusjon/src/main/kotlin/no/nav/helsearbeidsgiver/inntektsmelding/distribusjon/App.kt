package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.kafka.Producer
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-distribusjon".logger()

fun main() {
    val producer = Producer("helsearbeidsgiver.inntektsmelding")

    RapidApplication
        .create(System.getenv())
        .createDistribusjonRiver(producer)
        .registerShutdownLifecycle {
            producer.close()
        }.start()
}

fun RapidsConnection.createDistribusjonRiver(producer: Producer): RapidsConnection =
    also {
        logger.info("Starter ${DistribusjonRiver::class.simpleName}...")
        DistribusjonRiver(producer).connect(this)
    }
