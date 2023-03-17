package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DataKanal

class GenericDataPackageListener<T : Enum<*>>(
    val dataFelter: Array<T>,
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
            dataFelter.forEach { datafelt: Enum<*> ->
                it.interestedIn(datafelt.name)
            }
        }
    }

    override fun onData(packet: JsonMessage) {
        if (collectData(packet)) {
            mainListener.onPacket(packet, rapidsConnection)
        } else {
            // @TODO fiks logging logger.warn("Unrecognized package with uuid:" + packet[Key.UUID.str])
        }
    }

    fun collectData(message: JsonMessage): Boolean {
        // Akkuratt nå bare svarer med 1 data element men kan svare med mange
        val data = dataFelter.filter { dataFelt: Enum<*> ->
            message[dataFelt.name].asText().isNotEmpty()
        }.map { dataFelt: Enum<*> ->
            Pair(dataFelt.name, message[dataFelt.name])
        }.ifEmpty {
            return false
        }.first()

        redisStore.set(message[Key.UUID.str].asText() + data!!.first, data!!.second.asText())
        return true
    }
}
