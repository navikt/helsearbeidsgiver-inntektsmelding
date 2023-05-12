package no.nav.helsearbeidsgiver.felles.json.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDateTime

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("java.time.LocalDateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDateTime) = value.toString().let(encoder::encodeString)
    override fun deserialize(decoder: Decoder): LocalDateTime =
        decoder.decodeString()
            .runCatching(LocalDateTime::parse)
            .getOrElse { throw SerializationException(it) }
}
