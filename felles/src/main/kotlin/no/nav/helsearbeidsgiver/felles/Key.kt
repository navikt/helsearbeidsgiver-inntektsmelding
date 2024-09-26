package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.utils.json.serializer.AsStringSerializer

interface IKey {
    val str: String
}

@Serializable(KeySerializer::class)
enum class Key(
    override val str: String,
) : IKey {
    // Predefinerte fra rapids-and-rivers-biblioteket
    EVENT_NAME("@event_name"),
    BEHOV("@behov"),

    // Egendefinerte
    SELVBESTEMT_ID("selvbestemt_id"),
    IDENTITETSNUMMER("identitetsnummer"),
    ARBEIDSGIVER_FNR("arbeidsgiver_fnr"),
    UUID("uuid"),
    FORESPOERSEL_ID("forespoerselId"),
    VEDTAKSPERIODE_ID_LISTE("vedtaksperiode_id_liste"),
    JOURNALPOST_ID("journalpostId"),
    DATA("data"),
    FAIL("fail"),
    SKJEMA_INNTEKTSMELDING("skjema_inntektsmelding"),
    INNTEKTSMELDING("inntektsmelding"),
    LAGRET_INNTEKTSMELDING("lagret_inntektsmelding"),
    SELVBESTEMT_INNTEKTSMELDING("selvbestemt_inntektsmelding"),

    // Tidligere DataFelt
    VIRKSOMHET("virksomhet"),
    VIRKSOMHETER("virksomheter"),
    ARBEIDSTAKER_INFORMASJON("arbeidstakerInformasjon"),
    INNTEKTSMELDING_DOKUMENT("inntektsmelding_dokument"),
    ARBEIDSFORHOLD("arbeidsforhold"),
    SAK_ID("sak_id"),
    PERSISTERT_SAK_ID("persistert_sak_id"),
    OPPGAVE_ID("oppgave_id"),
    ORGNRUNDERENHET("orgnrUnderenhet"),
    ORGNR_UNDERENHETER("orgnr_underenheter"),
    ORG_RETTIGHETER("org_rettigheter"),
    FORESPOERSEL_SVAR("forespoersel-svar"),
    FORESPOERSLER_SVAR("forespoersler_svar"),
    INNTEKT("inntekt"),
    FNR("fnr"),
    FNR_LISTE("fnr_liste"),
    PERSONER("personer"),
    INNTEKTSDATO("inntektsdato"),
    TILGANG("tilgang"),
    SPINN_INNTEKTSMELDING_ID("spinnInntektsmeldingId"),
    EKSTERN_INNTEKTSMELDING("eksternInntektsmelding"),
    ER_DUPLIKAT_IM("er_duplikat_im"),
    INNSENDING_ID("innsending_id"),
    ;

    override fun toString(): String = str

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
    parse = Key::fromString,
)
