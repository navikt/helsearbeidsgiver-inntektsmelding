package no.nav.helsearbeidsgiver.felles.kafka

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.kafka.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.utils.fromEnv
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class Producer(
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
