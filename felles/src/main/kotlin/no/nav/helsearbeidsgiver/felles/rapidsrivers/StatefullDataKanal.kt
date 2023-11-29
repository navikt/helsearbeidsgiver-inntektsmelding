package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail.Companion.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class StatefullDataKanal(
    private val dataFelter: Array<Key>,
    override val eventName: EventName,
    private val mainListener: River.PacketListener,
    rapidsConnection: RapidsConnection,
    val redisStore: RedisStore
) : DataKanal(
    rapidsConnection
) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.EVENT_NAME.str, eventName.name)
            it.demandKey(Key.DATA.str)
            dataFelter.forEach { datafelt ->
                it.interestedIn(datafelt)
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
            val fail = Fail(
                feilmelding = "TransaksjonsID / UUID kan ikke vare tom da man bruker Composite Service",
                event = eventName,
                transaksjonId = UUID.randomUUID(),
                forespoerselId = packet[Key.FORESPOERSEL_ID.str].asText().runCatching(UUID::fromString).getOrNull(),
                utloesendeMelding = packet.toJson().parseJson()
            )

            rapidsConnection.publish(fail)
        } else if (collectData(packet)) {
            sikkerLogger.info("data collected for event ${eventName.name} med packet\n${packet.toPretty()}")
            mainListener.onPacket(packet, rapidsConnection)
        } else {
            sikkerLogger.warn("Mangler data for ${packet.toPretty()}")
            // @TODO fiks logging logger.warn("Unrecognized package with uuid:" + packet[Key.UUID.str])
        }
    }

    private fun collectData(message: JsonMessage): Boolean {
        // putt alle mottatte datafelter fra pakke i redis
        val dataMap = dataFelter.filter { dataFelt ->
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

    fun isAllDataCollected(transaksjonId: UUID): Boolean {
        val allKeys = dataFelter.map { RedisKey.of(transaksjonId, it) }.toTypedArray()
        val numKeysInRedis = redisStore.exist(*allKeys)
        logger.info("found " + numKeysInRedis)
        return numKeysInRedis == dataFelter.size.toLong()
    }
    fun isDataCollected(vararg keys: RedisKey): Boolean {
        return redisStore.exist(*keys) == keys.size.toLong()
    }
}
