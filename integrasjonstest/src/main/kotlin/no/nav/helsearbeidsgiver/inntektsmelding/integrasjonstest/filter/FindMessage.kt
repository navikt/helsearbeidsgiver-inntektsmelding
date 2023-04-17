package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.filter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.contains
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key

fun findMessage(
    meldinger: List<JsonNode>,
    event: EventName,
    behovType: BehovType? = null,
    datafelt: DataFelt? = null,
    løsning: Boolean? = false
): List<JsonNode> {
    return meldinger
        .filter { it.get(Key.EVENT_NAME.str).asText() == event.name }
        .filter { behovType == null || it.getBehov().contains(behovType) }
        .filter { datafelt == null || (it.contains(Key.DATA.str) && it.contains(datafelt.str)) }
        .filter { løsning == null || (!løsning || it.hasNonNull(Key.LØSNING.str)) }
}
