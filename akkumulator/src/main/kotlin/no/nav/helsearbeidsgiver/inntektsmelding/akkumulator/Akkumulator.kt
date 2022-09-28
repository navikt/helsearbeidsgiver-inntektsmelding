@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

class Akkumulator(
    rapidsConnection: RapidsConnection,
    private val redisStore: RedisStore,
    private val timeout: Long = 600
) : River.PacketListener {

    val objectMapper = ObjectMapper()

    init {
        River(rapidsConnection).apply {
            validate {
                it.requireKey("@id")
                it.requireKey("uuid")
                it.requireKey("@behov") //
                it.demandKey("@løsning")
            }
        }.register(this)
    }

    fun getRedisKey(uuid: String, løser: String): String {
        return "${uuid}_$løser"
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        sikkerlogg.info("Pakke med behov: $packet")
        val uuid = packet["uuid"].asText()
        logger.info("Behov: $uuid")

        val mangler = mutableListOf<String>()
        val feil = mutableListOf<String>()
        val results: ObjectNode = objectMapper.createObjectNode()

        // Finn alle løsninger og lagre ny til Redis
        packet["@behov"].map { it.asText() }.toList().forEach { behovNavn ->
            val løsning = packet["@løsning"].get(behovNavn) // Finn løsning JSON
            if (løsning == null) { // Fant ikke løsning i pakke
                val stored = redisStore.get(getRedisKey(uuid, behovNavn))
                if (stored.isNullOrEmpty()) { // Ingenting i Redis
                    sikkerlogg.info("Behov: $behovNavn. Løsning: n/a")
                    mangler.add(behovNavn)
                } else { // Fant i Redis
                    val node = objectMapper.readTree(stored)
                    sikkerlogg.info("Behov: $behovNavn. Løsning: (Redis) $node")
                    results.putIfAbsent(behovNavn, node)
                }
            } else { // Fant løsning i pakke
                val data = løsning.toString()
                sikkerlogg.info("Behov: $behovNavn. Løsning: $data")
                // Lagre løsning
                redisStore.set(getRedisKey(uuid, behovNavn), data, timeout)
                val node = objectMapper.readTree(data)
                val errorNode = node.get("errors")
                if (errorNode != null && errorNode.size() > 0) {
                    feil.add(behovNavn)
                }
                results.putIfAbsent(behovNavn, node)
            }
        }

        if (feil.isNotEmpty()) {
            logger.info("Behov: $uuid har feil $feil")
            val data = results.toString()
            sikkerlogg.info("Publiserer løsning: $data")
            redisStore.set(uuid, data, timeout)
        } else if (mangler.isNotEmpty()) {
            logger.info("Behov: $uuid er ikke komplett ennå. Mangler $mangler")
        } else {
            logger.info("Behov: $uuid er komplett.")
            val data = results.toString()
            println("Komplett: " + data)
            sikkerlogg.info("Publiserer løsning: $data")
            redisStore.set(uuid, data, timeout)
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
