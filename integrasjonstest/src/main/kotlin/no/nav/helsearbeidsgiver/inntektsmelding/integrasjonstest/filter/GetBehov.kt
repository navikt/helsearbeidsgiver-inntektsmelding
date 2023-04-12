package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.filter

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key

fun JsonNode.getBehov(): List<BehovType> {
    val node = this.get(Key.BEHOV.str)
    if (this.get(Key.BEHOV.str) == null) {
        return emptyList()
    }
    if (node.isArray) {
        return node.toMutableList().map { BehovType.valueOf(it.textValue()) }
    }
    if (this.get(Key.BEHOV.str).asText().isBlank()) {
        return emptyList()
    }
    return listOf(BehovType.valueOf(node.asText()))
}
