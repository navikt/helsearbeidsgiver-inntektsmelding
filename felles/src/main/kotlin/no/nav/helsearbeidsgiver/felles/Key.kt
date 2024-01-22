package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.utils.json.serializer.AsStringSerializer

interface IKey {
    val str: String
}

@Serializable(KeySerializer::class)
enum class Key(override val str: String) : IKey {
    // Predefinerte fra rapids-and-rivers-biblioteket
    EVENT_NAME("@event_name"),
    BEHOV("@behov"),

    // Egendefinerte
    IDENTITETSNUMMER("identitetsnummer"),
    ARBEIDSGIVER_ID("arbeidsgiverId"),
    UUID("uuid"),
    CLIENT_ID("client_id"),
    FORESPOERSEL_ID("forespoerselId"),
    JOURNALPOST_ID("journalpostId"),
    DATA("data"),
    FAIL("fail"),

    // Tidligere DataFelt
    VIRKSOMHET("virksomhet"),
    VIRKSOMHETER("virksomheter"),
    ARBEIDSTAKER_INFORMASJON("arbeidstakerInformasjon"),
    ARBEIDSGIVER_INFORMASJON("arbeidsgiverInformasjon"),
    INNTEKTSMELDING_DOKUMENT("inntektsmelding_dokument"),
    ARBEIDSFORHOLD("arbeidsforhold"),
    SAK_ID("sak_id"),
    PERSISTERT_SAK_ID("persistert_sak_id"),
    OPPGAVE_ID("oppgave_id"),
    ORGNRUNDERENHET("orgnrUnderenhet"),
    ORGNRUNDERENHETER("orgnrUnderenheter"),
    ORG_RETTIGHETER("org_rettigheter"),
    INNTEKTSMELDING("inntektsmelding"),
    FORESPOERSEL_SVAR("forespoersel-svar"),
    TRENGER_INNTEKT("trenger-inntekt"),
    INNTEKT("inntekt"),
    FNR("fnr"),
    ARBEIDSGIVER_FNR("arbeidsgiverFnr"), // pga trengerService....
    SKJAERINGSTIDSPUNKT("skjaeringstidspunkt"),
    TILGANG("tilgang"),
    SPINN_INNTEKTSMELDING_ID("spinnInntektsmeldingId"),
    EKSTERN_INNTEKTSMELDING("eksternInntektsmelding"),
    ER_DUPLIKAT_IM("er_duplikat_im");

    override fun toString(): String =
        str

    companion object {
        internal fun fromString(key: String): Key =
            Key.entries.firstOrNull {
                key == it.toString()
            }
                ?: throw IllegalArgumentException("Fant ingen Key med verdi som matchet '$key'.")
    }
}

internal object KeySerializer : AsStringSerializer<Key>(
    serialName = "helsearbeidsgiver.kotlinx.felles.Key",
    parse = Key::fromString
)
