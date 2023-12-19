package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class StatefullEventListener(
    rapid: RapidsConnection,
    override val event: EventName,
    private val redisStore: RedisStore,
    private val dataKeys: List<Key>,
    private val onEventProcessed: (JsonMessage, MessageContext) -> Unit
) : EventListener(rapid) {
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.interestedIn(*dataKeys.toTypedArray())
        }
    }

    private fun collectData(packet: JsonMessage) {
        val transactionId = randomUuid()
        packet[Key.UUID.str] = transactionId.toString()

        dataKeys.associateWith {
            packet[it.str]
        }
            .onEach { (dataFelt, data) ->
                // TODO denne bør fjernes, all data bør behandles likt
                val dataAsString =
                    if (data.isTextual) {
                        data.asText()
                    } else {
                        data.toString()
                    }

                redisStore.set(RedisKey.of(transactionId, dataFelt), dataAsString)
            }
    }

    override fun onEvent(packet: JsonMessage) {
        sikkerLogger.info("Statefull event listener for event ${event.name} med packet \n${packet.toPretty()}")
        collectData(packet)
        onEventProcessed(packet, rapidsConnection)
    }
}
