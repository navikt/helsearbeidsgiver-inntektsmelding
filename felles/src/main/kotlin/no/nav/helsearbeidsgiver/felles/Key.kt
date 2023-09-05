package no.nav.helsearbeidsgiver.felles

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.Serializable
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.utils.json.serializer.AsStringSerializer

interface IKey {
    val str: String
}

@Serializable(KeySerializer::class)
enum class Key(override val str: String) : IKey {
    // Predefinerte fra rapids-and-rivers-biblioteket
    EVENT_NAME("@event_name"),
    BEHOV("@behov"),
    LØSNING("@løsning"),
    OPPRETTET("@opprettet"),

    // Egendefinerte
    IDENTITETSNUMMER("identitetsnummer"),
    ARBEIDSGIVER_ID("arbeidsgiverId"),
    INITIATE_ID("initiateId"),
    UUID("uuid"),
    CLIENT_ID("client_id"),
    TRANSACTION_ORIGIN("transaction_origin"),
    FORESPOERSEL_ID("forespoerselId"),
    JOURNALPOST_ID("journalpostId"),
    DATA("data"),
    FAIL("fail"),
    FAILED_BEHOV("failed-behov");

    override fun toString(): String =
        str

    companion object {
        internal fun fromJson(json: String): Key =
            Key.entries.firstOrNull {
                json == it.str
            }
                ?: throw IllegalArgumentException("Fant ingen Key med verdi som matchet '$json'.")
    }
}

@Serializable(DataFeltSerializer::class)
enum class DataFelt(override val str: String) : IKey {
    VIRKSOMHET("virksomhet"),
    ARBEIDSTAKER_INFORMASJON("arbeidstakerInformasjon"),
    ARBEIDSGIVER_INFORMASJON("arbeidsgiverInformasjon"),
    INNTEKTSMELDING_DOKUMENT("inntektsmelding_dokument"),
    ARBEIDSFORHOLD("arbeidsforhold"),
    SAK_ID("sak_id"),
    PERSISTERT_SAK_ID("persistert_sak_id"),
    OPPGAVE_ID("oppgave_id"),
    ORGNRUNDERENHET("orgnrUnderenhet"),
    FORESPOERSEL_ID("forespoerselId"),
    INNTEKTSMELDING("inntektsmelding"),
    FORESPOERSEL_SVAR("forespoersel-svar"),
    TRENGER_INNTEKT("trenger-inntekt"),
    INNTEKT("inntekt"),
    FNR("fnr"),
    ARBEIDSGIVER_FNR("arbeidsgiverFnr"), // pga trengerService....
    SKJAERINGSTIDSPUNKT("skjaeringstidspunkt"),
    TILGANG("tilgang"),
    SPINN_INNTEKTSMELDING_ID("spinnInntektsmeldingId"),
    AVSENDER_SYSTEM_DATA("avsenderSystemData");
    override fun toString(): String =
        str

    companion object {
        internal fun fromJson(json: String): DataFelt =
            DataFelt.entries.firstOrNull {
                json == it.str
            }
                ?: throw IllegalArgumentException("Fant ingen DataFelt med verdi som matchet '$json'.")
    }
}

fun JsonMessage.value(key: IKey): JsonNode =
    this[key.str]

fun JsonMessage.valueNullable(key: IKey): JsonNode? =
    value(key).takeUnless(JsonNode::isMissingOrNull)

fun JsonMessage.valueNullableOrUndefined(key: IKey): JsonNode? =
    try { value(key).takeUnless(JsonNode::isMissingOrNull) } catch (e: IllegalArgumentException) { null }

internal object KeySerializer : AsStringSerializer<Key>(
    serialName = "helsearbeidsgiver.kotlinx.felles.Key",
    parse = Key::fromJson
)

internal object DataFeltSerializer : AsStringSerializer<DataFelt>(
    serialName = "helsearbeidsgiver.kotlinx.felles.DataFelt",
    parse = DataFelt::fromJson
)
