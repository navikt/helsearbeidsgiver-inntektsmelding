package no.nav.helsearbeidsgiver.felles.loeser

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.value

private const val DEFAULT_ERROR_MESSAGE = "Ukjent feil."

/**
 * Implementerer logikken for rapids-and-rivers.
 *
 * Bruker [Løser] for å lytte etter behov og løse dem.
 */
internal class PacketSolver(
    private val løser: Løser
) : River.PacketListener {
    init {
        val connection = RapidApplication.create(System.getenv())

        River(connection)
            .apply {
                validate { packet ->
                    packet.demandAll(Key.BEHOV.str, løser.behovType)
                    packet.rejectKey(Key.LØSNING.str)
                    packet.interestedIn(Key.INITIATE_ID.str)

                    løser.behovReadingKeys.forEach { packet.requireKey(it.str) }
                }
            }
            .register(this)

        connection.start()
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val løsning = runCatching {
            løser.løsBehov(packet)
        }
            .map(::LøsningSuccess)
            .getOrElse {
                it.message
                    .orDefault(DEFAULT_ERROR_MESSAGE)
                    .let(::LøsningFailure)
            }

        val behovType = packet.value(Key.BEHOV)[0].asText()

        val answer = createPacket(
            packet,
            setOf(
                Key.BEHOV
            ),
            mapOf(
                Key.INITIATE_ID to packet.id,
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

private fun <T> T?.orDefault(default: T) =
    this ?: default
