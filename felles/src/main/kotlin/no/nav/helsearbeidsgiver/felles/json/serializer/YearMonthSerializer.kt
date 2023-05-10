package no.nav.helsearbeidsgiver.felles.json.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.YearMonth

object YearMonthSerializer : KSerializer<YearMonth> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("java.time.YearMonth", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: YearMonth) = value.toString().let(encoder::encodeString)
    override fun deserialize(decoder: Decoder): YearMonth = decoder.decodeString().let(YearMonth::parse)
}
