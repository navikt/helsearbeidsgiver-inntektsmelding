package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class StatefullEventListener(
    val redisStore: IRedisStore,
    override val event: EventName,
    private val dataFelter: Array<String>,
    private val mainListener: River.PacketListener,
    rapidsConnection: RapidsConnection
) : EventListener(
    rapidsConnection
) {
    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.interestedIn(*dataFelter)
        }
    }

    private fun collectData(packet: JsonMessage) {
        val transactionId = UUID.randomUUID().toString()
        packet[Key.UUID.str] = transactionId

        dataFelter.map { dataFelt ->
            Pair(dataFelt, packet[dataFelt])
        }.forEach { data ->
            val str = if (data.second.isTextual) { data.second.asText() } else data.second.toString()
            redisStore.set(transactionId + data.first, str)
        }
    }
    override fun onEvent(packet: JsonMessage) {
        sikkerLogger().info("Statefull event listener for event ${event.name} med packet \n${packet.toPretty()}")
        collectData(packet)
        mainListener.onPacket(packet, rapidsConnection)
    }
}
