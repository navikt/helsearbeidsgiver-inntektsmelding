@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.value
import no.nav.helsearbeidsgiver.felles.valueNullable
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMap
import no.nav.helsearbeidsgiver.utils.json.serializer.list

class Akkumulator(
    private val rapidsConnection: RapidsConnection,
    private val redisStore: IRedisStore,
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
                    Key.BOOMERANG.str,
                    Key.INITIATE_ID.str,
                    Key.UUID.str,
                    Key.NESTE_BEHOV.str
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        sikkerLogger.debug("Fikk pakke ${packet.toJson()}")
        val boomerang = Key.BOOMERANG.let(packet::valueNullable)
            ?.toJsonElement()
            ?.fromJsonMap(Key.serializer())
            .orEmpty()

        val uuid = boomerang[Key.INITIATE_ID]?.fromJson(String.serializer())
            ?: boomerang[Key.UUID]?.fromJson(String.serializer())
            ?: packet.value(Key.UUID).asText()
                .let {
                    if (it.isNullOrEmpty()) {
                        packet.value(Key.INITIATE_ID).asText()
                            .takeUnless(String::isNullOrBlank)
                    } else {
                        it
                    }
                }

        if (uuid == null) {
            logger.error("Mangler UUID.")
            return
        }

        val behovListe = packet.value(Key.BEHOV).map(JsonNode::asText)
        val nesteBehov = boomerang[Key.NESTE_BEHOV]?.fromJson(BehovType.serializer().list())?.takeUnless(List<BehovType>::isEmpty)
            ?: packet.value(Key.NESTE_BEHOV)
                .map(JsonNode::asText)
                .map(BehovType::valueOf)

        "Behov: $behovListe Neste: $nesteBehov Uuid: $uuid".let {
            logger.info(it)
            sikkerLogger.info("$it Pakke: ${packet.toJson()}")
        }

        val mangler = mutableListOf<String>()
        val feil = mutableListOf<String>()
        val results: ObjectNode = objectMapper.createObjectNode()

        // Finn alle løsninger og lagre ny til Redis
        behovListe.forEach { behovType ->
            val redisKey = "${uuid}_$behovType"

            // Finn løsning JSON
            val løsning = packet.value(Key.LØSNING)
                .get(behovType)
                ?.toString()

            if (løsning.isNullOrEmpty()) { // Fant ikke løsning i pakke
                val stored = redisStore.get(redisKey)
                if (stored.isNullOrEmpty()) { // Ingenting i Redis
                    sikkerLogger.info("Behov: $behovType. Løsning: n/a")
                    mangler.add(behovType)
                } else { // Fant i Redis
                    val node = objectMapper.readTree(stored)
                    sikkerLogger.info("Behov: $behovType. Løsning: (Redis) $node")
                    results.putIfAbsent(behovType, node)
                }
            } else { // Fant løsning i pakke
                sikkerLogger.info("Behov: $behovType. Løsning: $løsning")
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
                sikkerLogger.info("Publiserer løsning: $data")
                redisStore.set(uuid, data, timeout)
            }
            mangler.isNotEmpty() -> {
                logger.info("Behov: $uuid er ikke komplett ennå. Mangler $mangler")
            }
            else -> {
                val data = results.toString()
                if (nesteBehov.isEmpty()) {
                    logger.info("Behov: $uuid er komplett.")
                    sikkerLogger.info("Publiserer løsning: $data")
                    redisStore.set(uuid, data, timeout)
                } else {
                    logger.info("Legger til Neste behov $nesteBehov")
                    val ny = hentNesteBehov(results, packet, objectMapper).toString()
                    sikkerLogger.info("Legger til Neste behov $nesteBehov Json: $ny")
                    rapidsConnection.publish(ny)
                }
            }
        }
    }
}
