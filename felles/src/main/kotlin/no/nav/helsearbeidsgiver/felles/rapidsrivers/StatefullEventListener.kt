package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class StatefullEventListener(
    override val event: EventName,
    private val dataKeys: Set<Key>,
    rapid: RapidsConnection,
    private val redisStore: RedisStore,
    private val onEventProcessed: (JsonMessage, MessageContext) -> Unit
) : EventListener(rapid) {
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.interestedIn(*dataKeys.toTypedArray())
        }
    }

    override fun onEvent(packet: JsonMessage) {
        sikkerLogger.info("Statefull event listener for event ${event.name} med packet \n${packet.toPretty()}")
        lagreData(packet)
        onEventProcessed(packet, rapidsConnection)
    }

    private fun lagreData(packet: JsonMessage) {
        val melding = packet.toJson().parseJson().toMap()

        val transaksjonId = randomUuid()

        // TODO fjern når client-ID er død
        packet[Key.UUID.str] = transaksjonId.toString()

        melding.plus(
            Key.UUID to transaksjonId.toJson()
        )
            .filterKeys(dataKeys::contains)
            .onEach { (key, data) ->
                redisStore.set(RedisKey.of(transaksjonId, key), data.toString())
            }
    }
}
