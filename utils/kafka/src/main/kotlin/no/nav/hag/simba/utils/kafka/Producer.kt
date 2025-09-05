package no.nav.hag.simba.utils.kafka

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.kontrakt.kafkatopic.pri.Pri
import no.nav.hag.simba.utils.kontrakt.kafkatopic.pri.toJson
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.JournalfoertInntektsmelding
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
    ) {
        send(key.toString(), message.toJson())
    }

    fun send(
        key: Fnr,
        message: Map<Key, JsonElement>,
    ) {
        send(key.verdi, message.toJson())
    }

    @JvmName("sendWithMessagePriKey")
    fun send(
        key: UUID,
        message: Map<Pri.Key, JsonElement>,
    ) {
        send(key.toString(), message.toJson())
    }

    /** Brukes til distribusjon. */
    fun send(inntektsmelding: JournalfoertInntektsmelding) {
        send(
            key =
                inntektsmelding.inntektsmelding.type.id
                    .toString(),
            message = inntektsmelding.toJson(JournalfoertInntektsmelding.serializer()),
        )
    }

    fun close() {
        producer.close()
    }

    private fun send(
        key: String,
        message: JsonElement,
    ) {
        producer
            .send(
                ProducerRecord(topic, key, message),
            ).get()
    }
}
