package no.nav.helsearbeidsgiver.felles.kafka

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.utils.fromEnv
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties
import org.apache.kafka.common.serialization.Serializer as KafkaSerializer

internal fun createProducer(): KafkaProducer<String, JsonElement> =
    KafkaProducer(
        kafkaProperties(),
        StringSerializer(),
        Serializer(),
    )

private fun kafkaProperties(): Properties {
    val env = Env()
    return Properties().apply {
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
                ProducerConfig.MAX_BLOCK_MS_CONFIG to "15000",
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to "1",
            ),
        )
    }
}

private class Env {
    val brokers = "KAFKA_BROKERS".fromEnv()
    val keystorePath = "KAFKA_KEYSTORE_PATH".fromEnv()
    val truststorePath = "KAFKA_TRUSTSTORE_PATH".fromEnv()
    val credstorePassword = "KAFKA_CREDSTORE_PASSWORD".fromEnv()
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
