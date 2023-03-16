package no.nav.helsearbeidsgiver.felles

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.isMissingOrNull

@Serializable(KeySerializer::class)
enum class Key(val str: String) {
    // Predefinerte fra rapids-and-rivers-biblioteket
    ID("@id"),
    EVENT_NAME("@event_name"),
    BEHOV("@behov"),
    LØSNING("@løsning"),
    OPPRETTET("@opprettet"),

    // Egendefinerte
    NOTIS("notis"),
    BOOMERANG("boomerang"),
    SESSION("session"),
    NESTE_BEHOV("neste_behov"),
    IDENTITETSNUMMER("identitetsnummer"),
    INITIATE_ID("initiateId"),
    UUID("uuid"),
    ORGNRUNDERENHET("orgnrUnderenhet"),
    ORGNR("orgnr"),
    FNR("fnr"),
    FORESPOERSEL_ID("forespoerselId"),
    INNTEKTSMELDING("inntektsmelding"),
    INNTEKTSMELDING_DOKUMENT("inntektsmelding_dokument"),
    JOURNALPOST_ID("journalpostId"),
    INNTEKT_DATO("inntektDato"),
    NAVN("navn"),
    SAK_ID("sak_id"),
    DATA("data"),
    FAIL("fail"),
    OPPGAVE_ID("oppgave_id");

    override fun toString(): String =
        str

    companion object {
        internal fun fromJson(json: String): Key? =
            Key.values().firstOrNull {
                json == it.str
            }
    }
}

fun JsonMessage.value(key: Key): JsonNode =
    this[key.str]

fun JsonMessage.valueNullable(key: Key): JsonNode? =
    value(key).takeUnless(JsonNode::isMissingOrNull)

internal object KeySerializer : KSerializer<Key> {
    override val descriptor = PrimitiveSerialDescriptor("helsearbeidsgiver.felles.Key", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Key) = value.str.let(encoder::encodeString)
    override fun deserialize(decoder: Decoder): Key = decoder.decodeString().let { json ->
        Key.fromJson(json)
            ?: throw SerializationException("Fant ingen Key med verdi som matchet '$json'.")
    }
}
