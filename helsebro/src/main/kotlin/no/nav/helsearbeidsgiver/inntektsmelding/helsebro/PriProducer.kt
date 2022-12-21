package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.TrengerForespoersel
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

class PriProducer(
    private val producer: KafkaProducer<String, TrengerForespoersel> = createProducer()
) {
    private val topic = Pri.TOPIC

    fun send(trengerForespoersel: TrengerForespoersel): Boolean =
        trengerForespoersel.toRecord()
            .runCatching {
                producer.send(this).get()
            }
            .isSuccess

    private fun <T : Any> T.toRecord(): ProducerRecord<String, T> =
        ProducerRecord(topic, this)
}

private fun createProducer(): KafkaProducer<String, TrengerForespoersel> =
    KafkaProducer(
        kafkaProperties(),
        StringSerializer(),
        TrengerForespoerselDataSerializer()
    )

private fun kafkaProperties(): Properties =
    Properties().apply {
        putAll(
            mapOf(
                CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to Env.Kafka.brokers,
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to SecurityProtocol.SSL.name,

                SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
                SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to "jks",
                SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to "PKCS12",
                SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to Env.Kafka.truststorePath,
                SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to Env.Kafka.credstorePassword,
                SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to Env.Kafka.keystorePath,
                SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to Env.Kafka.credstorePassword,

                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to "1"
            )
        )
    }

private class TrengerForespoerselDataSerializer : Serializer<TrengerForespoersel> {
    override fun serialize(topic: String, data: TrengerForespoersel): ByteArray =
        data.toJson()
            .toString()
            .toByteArray()
}
