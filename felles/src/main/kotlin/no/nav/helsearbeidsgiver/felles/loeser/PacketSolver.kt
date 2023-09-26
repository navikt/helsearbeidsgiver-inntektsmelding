package no.nav.helsearbeidsgiver.felles.loeser

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.loesning
import no.nav.helsearbeidsgiver.felles.json.toJsonNode
import no.nav.helsearbeidsgiver.felles.value
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.pipe.orDefault

private const val DEFAULT_ERROR_MESSAGE = "Ukjent feil."

/**
 * Implementerer logikken for rapids-and-rivers.
 *
 * Bruker [Loeser] for å lytte etter behov og løse dem.
 */
internal class PacketSolver(
    private val loeser: Loeser<*>
) : River.PacketListener {
    init {
        val connection = RapidApplication.create(System.getenv())

        River(connection)
            .apply {
                validate { packet ->
                    packet.demandAll(Key.BEHOV.str, loeser.behovType)
                    packet.rejectKey(Key.LØSNING.str)
                    packet.interestedIn(Key.INITIATE_ID.str)

                    loeser.behovReadingKeys.forEach { packet.requireKey(it.str) }
                }
            }
            .register(this)

        connection.start()
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val løsning = runCatching {
            loeser.løsBehov(packet)
        }
            .map { it.toLøsningSuccess() }
            .getOrElse {
                it.message
                    .orDefault(DEFAULT_ERROR_MESSAGE)
                    .toLøsningFailure()
            }
            .toJson(JsonElement.serializer().loesning())
            .toJsonNode()

        val behovType = packet.value(Key.BEHOV)[0].asText()

        val answer = createPacket(
            copyFrom = packet,
            copyFields = setOf(
                Key.BEHOV
            ),
            copyFieldOrDefaults = mapOf(
                Key.INITIATE_ID to packet.id
            ),
            // TODO Midlertidig map som verdi her. Endring av dette krever endring i akkumulator som krever endring i alle løsere.
            Key.LØSNING to mapOf(behovType to løsning)
        )

        context.publish(answer.toJson())
    }
}

private fun createPacket(
    copyFrom: JsonMessage,
    copyFields: Set<Key>,
    copyFieldOrDefaults: Map<Key, Any>,
    vararg newFields: Pair<Key, Any>
): JsonMessage {
    val copiedFields = copyFields.associateWith(copyFrom::value)
    val copiedFieldOrDefaults = copyFieldOrDefaults.mapValues { (key, default) ->
        copyFrom.value(key)
            .takeUnless(JsonNode::isMissingOrNull)
            ?: default
    }

    return copiedFields.plus(copiedFieldOrDefaults)
        .plus(newFields)
        .mapKeys { (key, _) -> key.str }
        .let(JsonMessage::newMessage)
}
