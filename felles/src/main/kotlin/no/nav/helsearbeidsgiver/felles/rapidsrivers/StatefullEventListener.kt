package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class StatefullEventListener(
    val redisStore: IRedisStore,
    override val event: EventName,
    val dataFelter: Array<String>,
    override val mainListener: River.PacketListener,
    rapidsConnection: RapidsConnection
) : DelegatingEventListener(
    mainListener,
    rapidsConnection
) {
    override fun accept(): River.PacketValidation = River.PacketValidation {
        it.interestedIn(*dataFelter)
    }

    fun collectData(packet: JsonMessage) {
        var uuid = packet[Key.UUID.str].asText()
        if (uuid.isNullOrEmpty()) {
            uuid = UUID.randomUUID().toString()
            packet.set(Key.UUID.str, uuid)
        }
        dataFelter.map { dataFelt ->
            Pair(dataFelt, packet[dataFelt])
        }.forEach { data ->
            val str = if (data.second.isTextual) { data.second.asText() } else data.second.toString()
            redisStore.set(uuid + data.first, str)
        }
    }
    override fun onEvent(packet: JsonMessage) {
        sikkerLogger().info("Statefull event listener for event ${event.name}" + " med paket  ${packet.toJson()}")
        collectData(packet)
        mainListener.onPacket(packet, rapidsConnection)
    }
}
