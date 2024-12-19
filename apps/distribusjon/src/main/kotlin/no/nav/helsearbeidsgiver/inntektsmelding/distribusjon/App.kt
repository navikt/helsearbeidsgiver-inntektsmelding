package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.utils.log.logger
import org.apache.kafka.clients.producer.KafkaProducer

private val logger = "im-distribusjon".logger()

fun main() {
    val producer = KafkaProducer<String, String>(kafkaProps())

    RapidApplication
        .create(System.getenv())
        .createDistribusjonRiver(producer)
        .registerShutdownLifecycle {
            producer.close()
        }.start()
}

fun RapidsConnection.createDistribusjonRiver(producer: KafkaProducer<String, String>): RapidsConnection =
    also {
        logger.info("Starter ${DistribusjonRiver::class.simpleName}...")
        DistribusjonRiver(producer).connect(this)
    }
