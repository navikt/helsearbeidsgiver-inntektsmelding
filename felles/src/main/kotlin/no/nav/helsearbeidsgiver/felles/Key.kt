package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.utils.json.serializer.AsStringSerializer

interface IKey

@Serializable(KeySerializer::class)
enum class Key : IKey {
    // Predefinerte fra rapids-and-rivers-biblioteket
    EVENT_NAME,
    BEHOV,

    // Egendefinerte
    ARBEIDSFORHOLD,
    ARBEIDSGIVER_FNR,
    BESTEMMENDE_FRAVAERSDAG,
    DATA,
    EKSTERN_INNTEKTSMELDING,
    ER_DUPLIKAT_IM,
    FAIL,
    FNR,
    FNR_LISTE,
    FORESPOERSEL,
    FORESPOERSEL_ID,
    FORESPOERSEL_MAP,
    FORESPOERSEL_SVAR,
    INNSENDING_ID,
    INNTEKT,
    INNTEKTSDATO,
    INNTEKTSMELDING,
    JOURNALPOST_ID,
    KONTEKST_ID,
    LAGRET_INNTEKTSMELDING,
    OPPGAVE_ID,
    ORGNR_UNDERENHET,
    ORGNR_UNDERENHETER,
    ORG_RETTIGHETER,
    PERSONER,
    SAK_ID,
    SELVBESTEMT_ID,
    SELVBESTEMT_INNTEKTSMELDING,
    SKAL_HA_PAAMINNELSE,
    SKJEMA_INNTEKTSMELDING,
    SPINN_INNTEKTSMELDING_ID,
    SVAR_KAFKA_KEY,
    SYKMELDT,
    TILGANG,
    VEDTAKSPERIODE_ID_LISTE,
    VIRKSOMHET,
    VIRKSOMHETER,
    ;

    override fun toString(): String =
        when (this) {
            EVENT_NAME -> "@event_name"
            BEHOV -> "@behov"
            else -> name.lowercase()
        }

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
