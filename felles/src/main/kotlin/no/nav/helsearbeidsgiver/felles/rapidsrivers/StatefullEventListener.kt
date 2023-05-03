package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.RedisStore

class StatefullEventListener(
    val redisStore: RedisStore,
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
        val uuid = packet[Key.UUID.str].asText()
        dataFelter.map { dataFelt ->
            Pair(dataFelt, packet[dataFelt])
        }.forEach { data ->
            val str = if (data!!.second.isTextual) { data!!.second.asText() } else data!!.second.toString()
            redisStore.set(uuid + data!!.first, str)
        }
    }
    override fun onEvent(packet: JsonMessage) {
        collectData(packet)
        mainListener.onPacket(packet, rapidsConnection)
    }
}
