package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.LoggerFactory

@Serializable
data class Løsere(
    val BrregLøser: String?,
    val PdlLøser: String?
)

class Akkumulator(rapidsConnection: RapidsConnection, private val redisStore: RedisStore) : River.PacketListener {
    private val logger = LoggerFactory.getLogger(this::class.java)
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandKey("@løsning")
                it.requireKey("@id")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        sikkerlogg.info("Akkumulerer pakke $packet")
        val id = packet["@id"].asText()
        val behov = packet["@behov"].asText()
        val løsning = packet["@løsning"].toString()
        logger.info("Akkumulerer id=$id behov=$behov løsning=$løsning")
        val løsninger = redisStore.get(id)
        if (løsninger.isNullOrEmpty()) {
            redisStore.set(id, løsning, 600)
        } else {
            // TODO: slå sammen løsninger og løsning på en god måte
            val sammenslåtteLøsninger = json.decodeFromString<Løsere>(løsning)
                .merge(json.decodeFromString(løsninger))
            redisStore.set(id, json.encodeToString(sammenslåtteLøsninger), 600)
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
