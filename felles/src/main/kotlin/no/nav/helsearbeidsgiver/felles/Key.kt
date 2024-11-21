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
    ARBEIDSFORHOLD("arbeidsforhold"),
    ARBEIDSGIVER_FNR("arbeidsgiver_fnr"),
    BESTEMMENDE_FRAVAERSDAG("bestemmende_fravaersdag"),
    DATA("data"),
    EKSTERN_INNTEKTSMELDING("ekstern_inntektsmelding"),
    ER_DUPLIKAT_IM("er_duplikat_im"),
    FAIL("fail"),
    FNR("fnr"),
    FNR_LISTE("fnr_liste"),
    FORESPOERSEL("forespoersel"),
    FORESPOERSEL_ID("forespoersel_id"),
    FORESPOERSEL_MAP("forespoersel_map"),
    FORESPOERSEL_SVAR("forespoersel_svar"),
    INNSENDING_ID("innsending_id"),
    INNTEKT("inntekt"),
    INNTEKTSDATO("inntektsdato"),
    INNTEKTSMELDING("inntektsmelding"),
    JOURNALPOST_ID("journalpost_id"),
    KONTEKST_ID("kontekst_id"),
    LAGRET_INNTEKTSMELDING("lagret_inntektsmelding"),
    OPPGAVE_ID("oppgave_id"),
    ORGNR_UNDERENHETER("orgnr_underenheter"),
    ORG_RETTIGHETER("org_rettigheter"),
    PERSONER("personer"),
    SAK_ID("sak_id"),
    SELVBESTEMT_ID("selvbestemt_id"),
    SELVBESTEMT_INNTEKTSMELDING("selvbestemt_inntektsmelding"),
    SKAL_HA_PAAMINNELSE("skal_ha_paaminnelse"),
    SKJEMA_INNTEKTSMELDING("skjema_inntektsmelding"),
    SYKMELDT("sykmeldt"),
    TILGANG("tilgang"),
    VEDTAKSPERIODE_ID_LISTE("vedtaksperiode_id_liste"),
    VIRKSOMHET("virksomhet"),
    VIRKSOMHETER("virksomheter"),

    // ulik formattering
    ORGNRUNDERENHET("orgnrUnderenhet"),
    ORGNRUNDERENHET_V2("orgnr_underenhet"),
    SPINN_INNTEKTSMELDING_ID("spinnInntektsmeldingId"),

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
