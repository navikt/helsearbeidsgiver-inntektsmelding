package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import java.util.UUID

/**
 * Sletter løsning og extra - legger til nytt behov. Samtidig beholde
 * alle andre verdier uendret
 */
fun mapExtraPacket(extra: BehovType, packet: JsonMessage, objectMapper: ObjectMapper): JsonNode {
    val jsonNode: JsonNode = objectMapper.readTree(packet.toJson())
    val list = jsonNode.get(Key.BEHOV.str).map(JsonNode::asText).map { it }.toMutableList()
    val arr = objectMapper.createArrayNode()
    list.forEach { arr.add(it) }
    arr.add(extra.name)
    (jsonNode as ObjectNode).apply {
        jsonNode.remove(Key.EXTRA.str)
        jsonNode.remove(Key.LØSNING.str)
        jsonNode.replace(Key.ID.str, TextNode(UUID.randomUUID().toString()))
        val arrNode = jsonNode.putArray(Key.BEHOV.str)
        arrNode.addAll(arr)
    }
    return jsonNode
}
