package no.nav.helsearbeidsgiver.felles

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage

enum class Key(val str: String) {
    // Predefinerte fra rapids-and-rivers-libet
    ID("@id"),
    EVENT_NAME("@event_name"),
    BEHOV("@behov"),
    LØSNING("@løsning"),
    OPPRETTET("@opprettet"),

    // Egendefinerte
    SESSION("session"),
    EXTRA("extra"),
    IDENTITETSNUMMER("identitetsnummer"),
    INITIATE_ID("initiate_id"),
    UUID("uuid");

    override fun toString(): String =
        str
}

fun JsonMessage.value(key: Key): JsonNode =
    this[key.str]
