package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key

/**
 * Sletter løsning og extra - legger til nytt behov. Samtidig beholde
 * alle andre verdier uendret
 *
 * @param løsninger En Json node med akkumulerte løsninger så langt
 * @param extraBehov Liste over kommende behov
 */
fun mapExtraPacket(løsninger: ObjectNode, packet: JsonMessage, objectMapper: ObjectMapper): JsonNode {
    val jsonNode: JsonNode = objectMapper.readTree(packet.toJson())
    (jsonNode as ObjectNode).apply {
        val nodeBehov = jsonNode.get(Key.BEHOV.str) as ArrayNode
        val nodeExtra = jsonNode.get(Key.EXTRA.str) as ArrayNode
        nodeBehov.flytt(nodeExtra)
        jsonNode.remove(Key.LØSNING.str)
        if (!løsninger.isEmpty) {
            jsonNode.put(Key.SESSION.str, løsninger)
        }
    }
    return jsonNode
}

fun ArrayNode.flytt(extra: ArrayNode) {
    val index = extra.indexOfFirst { it.getBehovType() == BehovType.PAUSE }
    if (index < 0) {
        this.addAll(extra)
        extra.removeAll()
        return
    }
    for (i in 0..index) {
        if (i == index) {
            extra.remove(0)
        } else {
            this.add(extra.remove(0))
        }
    }
}

fun JsonNode.getBehovType(): BehovType {
    return BehovType.valueOf(this.asText())
}
