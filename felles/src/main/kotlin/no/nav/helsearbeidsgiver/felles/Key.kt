package no.nav.helsearbeidsgiver.felles

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage

enum class Key(val str: String) {
    // Predefinerte fra rapids-and-rivers-biblioteket
    ID("@id"),
    EVENT_NAME("@event_name"),
    BEHOV("@behov"),
    LØSNING("@løsning"),
    OPPRETTET("@opprettet"),

    // Egendefinerte
    NOTIS("notis"),
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
    INNTEKTSMELDING_DOKUMENT("inntektsmelding_dokument");

    override fun toString(): String =
        str
}

fun JsonMessage.value(key: Key): JsonNode =
    this[key.str]
