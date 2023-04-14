package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.filter

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key

fun findMessage(meldinger: List<JsonNode>, event: EventName, behovType: BehovType? = null, løsning: Boolean? = false): List<JsonNode> {
    return meldinger
        .filter { it.get(Key.EVENT_NAME.str).asText() == event.name }
        .filter { behovType == null || it.getBehov().contains(behovType) }
        .filter { løsning == null || (!løsning || it.hasNonNull(Key.LØSNING.str)) }
}
