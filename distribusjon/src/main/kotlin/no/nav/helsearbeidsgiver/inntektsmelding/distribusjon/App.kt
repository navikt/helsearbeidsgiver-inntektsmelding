package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import org.apache.kafka.clients.producer.KafkaProducer

private val logger = "im-distribusjon".logger()
val sikkerLogger = sikkerLogger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createDistribusjon(KafkaProducer<String, String>(KafkaProperties(Kafka())))
        .start()
}

fun RapidsConnection.createDistribusjon(kafkaProducer: KafkaProducer<String, String>): RapidsConnection =
    also {
        logger.info("Starter ${DistribusjonLoeser::class.simpleName}...")
        DistribusjonLoeser(this, kafkaProducer)

        logger.info("Starter ${JournalfoertListener::class.simpleName}...")
        JournalfoertListener(this)
    }
