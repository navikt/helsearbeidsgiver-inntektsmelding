package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class StatefullDataKanal(
    rapid: RapidsConnection,
    override val event: EventName,
    private val redisStore: RedisStore,
    private val dataKeys: List<Key>,
    private val onDataCollected: (JsonMessage, MessageContext) -> Unit
) : DataKanal(rapid) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.EVENT_NAME.str, event.name)
            it.demandKey(Key.DATA.str)
            dataKeys.forEach { dataKey ->
                it.interestedIn(dataKey)
            }
        }
    }

    override fun onData(packet: JsonMessage) {
        if (packet[Key.FORESPOERSEL_ID.str].asText().isEmpty()) {
            logger.warn("Mangler forespørselId!")
            sikkerLogger.warn("Mangler forespørselId!")
        }
        if (packet[Key.UUID.str].asText().isNullOrEmpty()) {
            sikkerLogger.error("Transaksjon-ID er ikke initialisert for\n${packet.toPretty()}")
        } else if (collectData(packet)) {
            sikkerLogger.info("data collected for event ${event.name} med packet\n${packet.toPretty()}")
            onDataCollected(packet, rapid)
        } else {
            sikkerLogger.warn("Mangler data for ${packet.toPretty()}")
        }
    }

    private fun collectData(message: JsonMessage): Boolean {
        // putt alle mottatte datafelter fra pakke i redis
        val dataMap = dataKeys.filter { dataFelt ->
            !message[dataFelt.str].isMissingNode
        }
            .associateWith {
                message[it.str]
            }
            .onEach { (dataFelt, data) ->
                // TODO denne bør fjernes, all data bør behandles likt
                val dataAsString =
                    if (data.isTextual) {
                        data.asText()
                    } else {
                        data.toString()
                    }

                val transaksjonId = message[Key.UUID.str].asText().let(UUID::fromString)

                val dataKey = RedisKey.of(transaksjonId, dataFelt)

                redisStore.set(dataKey, dataAsString)
            }

        return dataMap.isNotEmpty()
    }
}
