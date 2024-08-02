package no.nav.helsearbeidsgiver.felles.rapidsrivers.river

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.utils.json.parseJson

/**
 * En [River] som implementerer logikken for rapids-and-rivers.
 * Leser alle meldinger ukritisk og mater dem til [haandterMelding].
 *
 * @property haandterMelding En funksjon som leser, filtrerer og prosesserer meldinger.
 */
internal class OpenRiver(
    rapid: RapidsConnection,
    private val haandterMelding: JsonElement.() -> Map<Key, JsonElement>?,
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
            .haandterMelding()
            ?.also(context::publish)
    }
}
