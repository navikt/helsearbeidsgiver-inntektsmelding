package no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models

import com.fasterxml.jackson.annotation.JsonValue
import kotlin.String
import kotlin.collections.Map

enum class NaturalytelseKode(
    @JsonValue
    val value: String
) {
    AKSJER_GRUNNFONDSBEVIS_TIL_UNDERKURS("AksjerGrunnfondsbevisTilUnderkurs"),

    LOSJI("Losji"),

    KOST_DOEGN("KostDoegn"),

    BESOEKSREISER_HJEMMET_ANNET("BesoeksreiserHjemmetAnnet"),

    KOSTBESPARELSE_IHJEMMET("KostbesparelseIHjemmet"),

    RENTEFORDEL_LAAN("RentefordelLaan"),

    BIL("Bil"),

    KOST_DAGER("KostDager"),

    BOLIG("Bolig"),

    SKATTEPLIKTIG_DEL_FORSIKRINGER("SkattepliktigDelForsikringer"),

    FRI_TRANSPORT("FriTransport"),

    OPSJONER("Opsjoner"),

    TILSKUDD_BARNEHAGEPLASS("TilskuddBarnehageplass"),

    ANNET("Annet"),

    BEDRIFTSBARNEHAGEPLASS("Bedriftsbarnehageplass"),

    YRKEBIL_TJENESTLIGBEHOV_KILOMETER("YrkebilTjenestligbehovKilometer"),

    YRKEBIL_TJENESTLIGBEHOV_LISTEPRIS("YrkebilTjenestligbehovListepris"),

    INNBETALING_TIL_UTENLANDSK_PENSJONSORDNING("InnbetalingTilUtenlandskPensjonsordning"),

    ELEKTRONISK_KOMMUNIKASJON("ElektroniskKommunikasjon");

    companion object {
        private val mapping: Map<String, NaturalytelseKode> =
            values().associateBy(NaturalytelseKode::value)

        fun fromValue(value: String): NaturalytelseKode? = mapping[value]
    }
}
