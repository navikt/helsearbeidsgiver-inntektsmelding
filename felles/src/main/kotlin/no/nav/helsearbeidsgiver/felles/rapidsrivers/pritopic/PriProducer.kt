package no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.fromEnv
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

class PriProducer(
    private val producer: KafkaProducer<String, JsonElement>,
) {
    constructor(env: Env = defaultEnv()) : this(
        createProducer(env),
    )

    private val topic = Pri.TOPIC

    fun send(vararg message: Pair<Pri.Key, JsonElement>): Result<JsonElement> =
        message
            .toMap()
            .toJson()
            .let(::send)

    fun send(message: JsonElement): Result<JsonElement> =
        message
            .toRecord()
            .runCatching {
                producer.send(this).get()
            }.map { message }

    private fun JsonElement.toRecord(): ProducerRecord<String, JsonElement> = ProducerRecord(topic, this)

    interface Env {
        val brokers: String
        val keystorePath: String
        val truststorePath: String
        val credstorePassword: String
    }

    companion object {
        fun defaultEnv(): Env =
            object : Env {
                override val brokers = "KAFKA_BROKERS".fromEnv()
                override val keystorePath = "KAFKA_KEYSTORE_PATH".fromEnv()
                override val truststorePath = "KAFKA_TRUSTSTORE_PATH".fromEnv()
                override val credstorePassword = "KAFKA_CREDSTORE_PASSWORD".fromEnv()
            }
    }
}

private fun Map<Pri.Key, JsonElement>.toJson(): JsonElement =
    toJson(
        MapSerializer(
            Pri.Key.serializer(),
            JsonElement.serializer(),
        ),
    )

private fun createProducer(env: PriProducer.Env): KafkaProducer<String, JsonElement> =
    KafkaProducer(
        kafkaProperties(env),
        StringSerializer(),
        Serializer(),
    )

private fun kafkaProperties(env: PriProducer.Env): Properties =
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
