package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import no.nav.helsearbeidsgiver.felles.utils.fromEnv
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import java.util.Properties

object EnvKafka {
    val brokers = "KAFKA_BROKERS".fromEnv()
    val keystorePath = "KAFKA_KEYSTORE_PATH".fromEnv()
    val truststorePath = "KAFKA_TRUSTSTORE_PATH".fromEnv()
    val credstorePassword = "KAFKA_CREDSTORE_PASSWORD".fromEnv()
}

fun kafkaProps(): Properties =
    Properties().apply {
        putAll(
            mapOf(
                CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to EnvKafka.brokers,
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to SecurityProtocol.SSL.name,
                SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
                SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to "jks",
                SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to "PKCS12",
                SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to EnvKafka.truststorePath,
                SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to EnvKafka.credstorePassword,
                SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to EnvKafka.keystorePath,
                SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to EnvKafka.credstorePassword,
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to "1",
                ProducerConfig.ACKS_CONFIG to "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to "true",
                ProducerConfig.MAX_BLOCK_MS_CONFIG to "15000",
                ProducerConfig.RETRIES_CONFIG to "2",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer",
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer",
            ),
        )
    }
