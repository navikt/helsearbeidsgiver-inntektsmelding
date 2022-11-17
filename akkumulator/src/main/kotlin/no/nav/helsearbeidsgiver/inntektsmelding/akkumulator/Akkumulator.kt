@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.value

class Akkumulator(
    val rapidsConnection: RapidsConnection,
    private val redisStore: RedisStore,
    private val timeout: Long = 600
) : River.PacketListener {

    private val objectMapper = ObjectMapper()

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandKey(Key.LØSNING.str)
                it.requireKey(
                    Key.BEHOV.str
                )
                it.interestedIn(
                    Key.INITIATE_ID.str,
                    Key.UUID.str,
                    Key.IDENTITETSNUMMER.str,
                    Key.NESTE_BEHOV.str
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet.value(Key.UUID).asText()
            .let {
                if (it.isNullOrEmpty()) {
                    packet.value(Key.INITIATE_ID).asText()
                } else {
                    it
                }
            }
        val eventName = packet.value(Key.EVENT_NAME).asText()
        val behov = packet.value(Key.BEHOV).asText()
        val nesteBehov = packet.get(Key.NESTE_BEHOV.str).map(JsonNode::asText).map { BehovType.valueOf(it) }.toMutableList()
        val identitetsnummer = packet.value(Key.IDENTITETSNUMMER).asText()
        sikkerlogg.info("Event: $eventName Behov: $behov Neste: $nesteBehov Fnr: $identitetsnummer Uuid: $uuid Pakke: ${packet.toJson()}")
        logger.info("Event: $eventName Behov: $behov Neste: $nesteBehov Uuid: $uuid")
        val mangler = mutableListOf<String>()
        val feil = mutableListOf<String>()
        val results: ObjectNode = objectMapper.createObjectNode()

        // Finn alle løsninger og lagre ny til Redis
        packet.value(Key.BEHOV).map(JsonNode::asText)
            .forEach { behovType ->
                val redisKey = "${uuid}_$behovType"

                // Finn løsning JSON
                val løsning = packet.value(Key.LØSNING)
                    .get(behovType)
                    ?.toString()

                if (løsning.isNullOrEmpty()) { // Fant ikke løsning i pakke
                    val stored = redisStore.get(redisKey)
                    if (stored.isNullOrEmpty()) { // Ingenting i Redis
                        sikkerlogg.info("Behov: $behovType. Løsning: n/a")
                        mangler.add(behovType)
                    } else { // Fant i Redis
                        val node = objectMapper.readTree(stored)
                        sikkerlogg.info("Behov: $behovType. Løsning: (Redis) $node")
                        results.putIfAbsent(behovType, node)
                    }
                } else { // Fant løsning i pakke
                    sikkerlogg.info("Behov: $behovType. Løsning: $løsning")
                    // Lagre løsning
                    redisStore.set(redisKey, løsning, timeout)

                    val node = objectMapper.readTree(løsning)
                    val errorNode = node.get("error")
                    if (errorNode != null && !errorNode.isMissingOrNull()) {
                        feil.add(behovType)
                    }

                    results.putIfAbsent(behovType, node)
                }
            }

        when {
            feil.isNotEmpty() -> {
                val data = results.toString()
                logger.info("Behov: $uuid har feil $feil")
                sikkerlogg.info("Publiserer løsning: $data")
                redisStore.set(uuid, data, timeout)
            }
            mangler.isNotEmpty() -> {
                logger.info("Behov: $uuid er ikke komplett ennå. Mangler $mangler")
            }
            else -> {
                val data = results.toString()
                if (nesteBehov.isEmpty()) {
                    logger.info("Behov: $uuid er komplett.")
                    sikkerlogg.info("Publiserer løsning: $data")
                    redisStore.set(uuid, data, timeout)
                } else {
                    logger.info("Legger til Neste behov $nesteBehov")
                    val ny = hentNesteBehov(results, packet, objectMapper).toString()
                    sikkerlogg.info("Legger til Neste behov $nesteBehov Json: $ny")
                    rapidsConnection.publish(identitetsnummer, ny)
                }
            }
        }
    }
}
