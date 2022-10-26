package no.nav.helsearbeidsgiver.felles.loeser

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.River
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

        val answer = createPacket(
            packet to setOf(
                Key.BEHOV
            ),
            // TODO Midlertidig map som verdi her. Endring av dette krever endring i akkumulator som krever endring i alle løsere.
            Key.LØSNING to mapOf(Key.BEHOV.str to løsning)
        )

        context.publish(answer.toJson())
    }
}

private fun createPacket(
    copyFieldsFrom: Pair<JsonMessage, Set<Key>>,
    vararg newFields: Pair<Key, Any>
): JsonMessage {
    val (copyPacket, copyKeys) = copyFieldsFrom
    return copyKeys.associateWith(copyPacket::value)
        .plus(newFields)
        .mapKeys { (key, _) -> key.str }
        .let(JsonMessage::newMessage)
}

private fun <T> T?.orDefault(default: T) =
    this ?: default
