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
    private val log = LoggerFactory.getLogger(this::class.java)
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
        val id = packet["@id"].asText()
        log.info("Akkumulerer løsning med id $id")

        val løsning = packet["@løsning"].toString()

        val løsninger = redisStore.get(id)

        if (løsninger.isNullOrEmpty()) {
            redisStore.set(id, løsning)
        } else {
            // TODO: slå sammen løsninger og løsning på en god måte
            val sammenslåtteLøsninger = json.decodeFromString<Løsere>(løsning)
                .merge(json.decodeFromString(løsninger))

            redisStore.set(id, json.encodeToString(sammenslåtteLøsninger))
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
