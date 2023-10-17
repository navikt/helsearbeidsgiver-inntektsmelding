package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import no.nav.helsearbeidsgiver.felles.fromEnv
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import java.util.Properties

data class Kafka(
    val brokers: String = "KAFKA_BROKERS".fromEnv(),
    val keystorePath: String = "KAFKA_KEYSTORE_PATH".fromEnv(),
    val truststorePath: String = "KAFKA_TRUSTSTORE_PATH".fromEnv(),
    val credstorePassword: String = "KAFKA_CREDSTORE_PASSWORD".fromEnv()
)

fun KafkaProperties(kafka: Kafka): Properties =
    Properties().apply {
        putAll(
            mapOf(
                CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to kafka.brokers,
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to SecurityProtocol.SSL.name,

                SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
                SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to "jks",
                SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to "PKCS12",
                SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to kafka.truststorePath,
                SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to kafka.credstorePassword,
                SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to kafka.keystorePath,
                SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to kafka.credstorePassword,

                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to "1",
                ProducerConfig.ACKS_CONFIG to "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to "true",
                ProducerConfig.MAX_BLOCK_MS_CONFIG to "15000",
                ProducerConfig.RETRIES_CONFIG to "2",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer",
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer"
            )
        )
    }
