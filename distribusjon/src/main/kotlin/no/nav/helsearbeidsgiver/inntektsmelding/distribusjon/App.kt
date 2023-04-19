package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory

internal val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

fun main() {
    RapidApplication
        .create(System.getenv())
        .createDistribusjon(KafkaProducer<String, String>(KafkaProperties(Kafka())))
        .start()
}

fun RapidsConnection.createDistribusjon(kafkaProducer: KafkaProducer<String, String>): RapidsConnection {
    DistribusjonLøser(this, kafkaProducer)
    JournalførtListener(this)
    return this
}
