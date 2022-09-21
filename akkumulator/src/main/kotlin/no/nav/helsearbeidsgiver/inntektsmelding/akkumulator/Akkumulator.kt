package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

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
            val v = packet["@løsning"].get(it)
            if (v != null) {
                redisStore.set(getRedisKey(uuid, it), v.asText(), timeout)
            }
        }

        // Sjekk behov opp mot lagret i Redis
        val komplettMap = mutableMapOf<String, String>()
        behovListe.forEach {
            val stored = redisStore.get(getRedisKey(uuid, it))
            komplettMap.put(it, stored ?: "")
        }

        // Er alle behov fylt ut?
        if (komplettMap.filterValues { it.isBlank() }.isEmpty()) {
            logger.info("Behov: $uuid er komplett.")
            val data = json.encodeToString(komplettMap)
            sikkerlogg.info("Publiserer løsning: $data")
            redisStore.set(uuid, data, timeout)
        } else {
            logger.info("Behov: $uuid er ikke komplett")
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
