package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key

/**
 * Flytter fra neste_behov til @behov. Samtidig beholde
 * alle andre verdier uendret
 *
 * @param løsninger En Json node med akkumulerte løsninger så langt
 */
fun hentNesteBehov(løsninger: ObjectNode, packet: JsonMessage, objectMapper: ObjectMapper): JsonNode {
    val jsonNode: JsonNode = objectMapper.readTree(packet.toJson())
    (jsonNode as ObjectNode).apply {
        val nodeBehov = jsonNode.get(Key.BEHOV.str) as ArrayNode
        val nodeNesteBehov = jsonNode.let {
            it.get(Key.BOOMERANG.str)
                ?.takeUnless(JsonNode::isMissingOrNull)
                ?.get(Key.NESTE_BEHOV.str)
                ?.takeUnless(JsonNode::isEmpty)
                ?: it.get(Key.NESTE_BEHOV.str)
        }
            .let { it as ArrayNode }
        nodeBehov.flyttBehov(nodeNesteBehov)
        jsonNode.remove(Key.LØSNING.str)
        if (!løsninger.isEmpty) {
            jsonNode.set(Key.SESSION.str, løsninger)
        } else {
            jsonNode.set(Key.SESSION.str, objectMapper.createObjectNode())
        }
    }
    return jsonNode
}

fun ArrayNode.flyttBehov(node: ArrayNode) {
    val index = node.indexOfFirst { it.getBehovType() == BehovType.PAUSE }
    if (index < 0) {
        this.addAll(node)
        node.removeAll()
        return
    }
    for (i in 0..index) {
        if (i == index) {
            node.remove(0)
        } else {
            this.add(node.remove(0))
        }
    }
}

fun JsonNode.getBehovType(): BehovType {
    return BehovType.valueOf(this.asText())
}
