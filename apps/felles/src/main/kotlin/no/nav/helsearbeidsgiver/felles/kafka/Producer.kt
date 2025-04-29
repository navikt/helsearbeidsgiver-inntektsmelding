package no.nav.helsearbeidsgiver.felles.kafka

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.kafka.pritopic.Pri
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.UUID

class Producer(
    private val topic: String,
    private val producer: KafkaProducer<String, JsonElement> = createProducer(),
) {
    fun send(
        key: UUID,
        message: Map<Pri.Key, JsonElement>,
    ): Result<JsonElement> = send(key.toString(), message.toJson())

    private fun send(
        key: String,
        message: JsonElement,
    ): Result<JsonElement> =
        ProducerRecord(topic, key, message)
            .runCatching {
                producer.send(this).get()
            }.map { message }
}

private fun Map<Pri.Key, JsonElement>.toJson(): JsonElement =
    toJson(
        MapSerializer(
            Pri.Key.serializer(),
            JsonElement.serializer(),
        ),
    )
