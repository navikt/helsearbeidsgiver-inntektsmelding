package no.nav.helsearbeidsgiver.felles.kafka

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties
import org.apache.kafka.common.serialization.Serializer as KafkaSerializer

internal fun createProducer(env: Producer.Env): KafkaProducer<String, JsonElement> =
    KafkaProducer(
        kafkaProperties(env),
        StringSerializer(),
        Serializer(),
    )

private fun kafkaProperties(env: Producer.Env): Properties =
    Properties().apply {
        putAll(
            mapOf(
                CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to env.brokers,
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to SecurityProtocol.SSL.name,
                SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
                SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to "jks",
                SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to env.truststorePath,
                SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to env.credstorePassword,
                SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to "PKCS12",
                SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to env.keystorePath,
                SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to env.credstorePassword,
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to "1",
            ),
        )
    }

private class Serializer : KafkaSerializer<JsonElement> {
    override fun serialize(
        topic: String,
        data: JsonElement,
    ): ByteArray =
        data
            .toJson(JsonElement.serializer())
            .toString()
            .toByteArray()
}
