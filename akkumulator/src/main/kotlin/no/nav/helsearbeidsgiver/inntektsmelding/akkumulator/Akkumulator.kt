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
        val behovListe = packet["@behov"].map { it.asText() }.toList()

        // Skal lagre løsning
        behovListe.forEach {
            val løserNavn = packet["@løsning"].get(it)
            sikkerlogg.info("Fant løsning $it for $løserNavn")
            if (løserNavn != null) {
                val data = løserNavn.toString()
                redisStore.set(getRedisKey(uuid, it), data, timeout)
            }
        }

        // Sjekk behov opp mot lagret i Redis
        val mangler = mutableListOf<String>()
        val results: ObjectNode = objectMapper.createObjectNode()

        behovListe.forEach {
            val stored = redisStore.get(getRedisKey(uuid, it))
            if (stored.isNullOrEmpty()){
                mangler.add(it)
            } else {
                val node = objectMapper.readTree(stored)
                sikkerlogg.info("Fant i redis: $node")
                results.putIfAbsent(it, node)
            }
        }

        // Er alle behov fylt ut?
        if (mangler.isNotEmpty()) {
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
