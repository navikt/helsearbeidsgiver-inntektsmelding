package no.nav.helsearbeidsgiver.felles.loeser

import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.utils.json.parseJson

/**
 * En [River] som implementerer logikken for rapids-and-rivers.
 * Leser alle meldinger ukritisk og mater dem til [messageHandler].
 *
 * @property messageHandler En [ObjectRiver] som brukes for å lese, filtrere og prosessere meldinger.
 */
internal class OpenRiver(
    rapid: RapidsConnection,
    private val messageHandler: ObjectRiver<*>,
) : River.PacketListener {
    init {
        River(rapid).register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        packet
            .toJson()
            .parseJson()
            .toMap()
            .filterValues { it !is JsonNull }
            .let(messageHandler::lesOgHaandter)
            ?.also(context::publish)
    }
}
