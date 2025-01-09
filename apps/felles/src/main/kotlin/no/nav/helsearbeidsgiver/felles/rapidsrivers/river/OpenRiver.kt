package no.nav.helsearbeidsgiver.felles.rapidsrivers.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
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
    private val haandterMelding: JsonElement.() -> Pair<KafkaKey?, Map<Key, JsonElement>>?,
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
            ?.also { (kafkaKey, melding) ->
                // TODO gjør key non-nullable
                context.publish(kafkaKey?.key, melding)
            }
    }
}
