package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser

class GenericDataPackageListener<T : Enum<*>>(
    val dataFelter: Array<T>,
    val mainListener: River.PacketListener,
    rapidsConnection: RapidsConnection,
    val redisStore: RedisStore
) : Løser(
    rapidsConnection
) {

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.EVENT_NAME.str, EventName.INSENDING_STARTED.name)
            it.demandKey(Key.DATA.str)
            dataFelter.forEach { datafelt: Enum<*> ->
                it.interestedIn(datafelt.name)
            }
        }
    }

    override fun onBehov(packet: JsonMessage) {
        collectData(packet)
        mainListener.onPacket(packet, rapidsConnection)
    }

    fun collectData(message: JsonMessage) {
        val data = dataFelter.filter { dataFelt: Enum<*> ->
            message[dataFelt.name].asText().isNotEmpty()
        }.map { dataFelt: Enum<*> ->
            Pair(dataFelt.name, message[dataFelt.name])
        }.first()

        redisStore.set(message[Key.UUID.str].asText() + data.first, data.second.asText())
    }
}
