package no.nav.helsearbeidsgiver.felles.kafka

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.JournalfoertInntektsmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.kafka.pritopic.Pri
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.UUID

class Producer(
    private val topic: String,
    private val producer: KafkaProducer<String, JsonElement> = createProducer(),
) {
    @JvmName("sendWithMessageKey")
    fun send(
        key: UUID,
        message: Map<Key, JsonElement>,
    ): Result<JsonElement> = send(key.toString(), message.toJson())

    fun send(
        key: Fnr,
        message: Map<Key, JsonElement>,
    ): Result<JsonElement> = send(key.verdi, message.toJson())

    @JvmName("sendWithMessagePriKey")
    fun send(
        key: UUID,
        message: Map<Pri.Key, JsonElement>,
    ): Result<JsonElement> = send(key.toString(), message.toJson())

    /** Brukes til distribusjon. */
    fun send(inntektsmelding: JournalfoertInntektsmelding): Result<JsonElement> =
        send(
            key =
                inntektsmelding.inntektsmelding.type.id
                    .toString(),
            message = inntektsmelding.toJson(JournalfoertInntektsmelding.serializer()),
        )

    fun close() {
        producer.close()
    }

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
