package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.prossesor

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.RedisStore

class GenericDataPackageListener(
    val dataFelter: Array<String>,
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
            dataFelter.forEach { datafelt ->
                it.interestedIn(datafelt)
            }
        }
    }

    override fun onBehov(packet: JsonMessage) {
        collectData(packet)
        mainListener.onPacket(packet, rapidsConnection)
    }

    fun collectData(message: JsonMessage) {
        val data = dataFelter.filter { dataFelt ->
            message[dataFelt].asText().isNotEmpty()
        }.map { dataFelt ->
            Pair(dataFelt, message[dataFelt])
        }.first()

        redisStore.set(message[Key.UUID.str].asText() + data!!.first, data!!.second.asText())
    }
}
