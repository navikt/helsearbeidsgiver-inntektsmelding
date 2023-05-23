package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.createFail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DataKanal
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class StatefullDataKanal(
    val dataFelter: Array<String>,
    override val eventName: EventName,
    val mainListener: River.PacketListener,
    rapidsConnection: RapidsConnection,
    val redisStore: RedisStore
) : DataKanal(
    rapidsConnection
) {

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.EVENT_NAME.str, eventName.name)
            it.demandKey(Key.DATA.str)
            dataFelter.forEach { datafelt ->
                it.interestedIn(datafelt)
            }
        }
    }
    fun validate(packet: JsonMessage) {
    }

    override fun onData(packet: JsonMessage) {
        if (packet[Key.UUID.str] == null || packet[Key.UUID.str].asText().isNullOrEmpty()) {
            sikkerLogger().error("TransaksjonsID er ikke initialisert for ${packet.toJson()}")
            rapidsConnection.publish(
                packet.createFail(
                    "TransaksjonsID / UUID kan ikke vare tom da man bruker Composite Service"
                ).toJsonMessage().toJson()
            )
        } else if (collectData(packet)) {
            sikkerLogger().info("data collected for event ${eventName.name} med packet ${packet.toJson()}")
            mainListener.onPacket(packet, rapidsConnection)
        } else {
            sikkerLogger().warn("Mangler data for $packet")
            // @TODO fiks logging logger.warn("Unrecognized package with uuid:" + packet[Key.UUID.str])
        }
    }

    fun collectData(message: JsonMessage): Boolean {
        // Akkuratt nå bare svarer med 1 data element men kan svare med mange
        val data = dataFelter.filter { dataFelt ->
            !message[dataFelt].isMissingNode
        }.map { dataFelt ->
            Pair(dataFelt, message[dataFelt])
        }.ifEmpty {
            return false
        }.first()
        val str = if (data.second.isTextual) { data.second.asText() } else data.second.toString()
        redisStore.set(message[Key.UUID.str].asText() + data.first, str)
        return true
    }

    fun isAllDataCollected(uuid: String): Boolean {
        return redisStore.exist(*dataFelter.map { uuid + it }.toTypedArray()) == dataFelter.size.toLong()
    }
    fun isDataCollected(vararg keys: String): Boolean {
        return redisStore.exist(*keys) == keys.size.toLong()
    }
}
