package no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic

import kotlinx.serialization.KSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties
import org.apache.kafka.common.serialization.Serializer as KafkaSerializer

class PriProducer<T : Any>(
    private val producer: KafkaProducer<String, T>
) {
    constructor(env: Env, serializer: KSerializer<T>) : this(
        createProducer(env, serializer)
    )

    private val topic = Pri.TOPIC

    fun send(message: T): Boolean =
        message.toRecord()
            .runCatching {
                producer.send(this).get()
            }
            .isSuccess

    private fun T.toRecord(): ProducerRecord<String, T> =
        ProducerRecord(topic, this)

    interface Env {
        val brokers: String
        val keystorePath: String
        val truststorePath: String
        val credstorePassword: String
    }
}

private fun <T : Any> createProducer(env: PriProducer.Env, serializer: KSerializer<T>): KafkaProducer<String, T> =
    KafkaProducer(
        kafkaProperties(env),
        StringSerializer(),
        Serializer(serializer)
    )

private fun kafkaProperties(env: PriProducer.Env): Properties =
    Properties().apply {
        putAll(
            mapOf(
                CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to env.brokers,
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to SecurityProtocol.SSL.name,

                SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
                SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to "jks",
                SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to "PKCS12",
                SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to env.truststorePath,
                SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to env.credstorePassword,
                SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to env.keystorePath,
                SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to env.credstorePassword,

                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to "1"
            )
        )
    }

private class Serializer<T : Any>(
    private val serializer: KSerializer<T>
) : KafkaSerializer<T> {
    override fun serialize(topic: String, data: T): ByteArray =
        data.toJson(serializer)
            .toString()
            .toByteArray()
}
