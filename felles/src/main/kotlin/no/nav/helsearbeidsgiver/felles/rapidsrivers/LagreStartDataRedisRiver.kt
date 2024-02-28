package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class LagreStartDataRedisRiver(
    private val eventName: EventName,
    private val dataKeys: Set<Key>,
    rapid: RapidsConnection,
    private val redisStore: RedisStore,
    private val etterDataLagret: (JsonMessage, MessageContext) -> Unit
) : River.PacketListener {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    init {
        River(rapid).apply {
            validate {
                it.demandValues(
                    Key.EVENT_NAME to eventName.name
                )
                it.rejectKeys(
                    Key.BEHOV,
                    Key.DATA,
                    Key.FAIL
                )
                it.interestedIn(
                    Key.UUID,
                    *dataKeys.toTypedArray()
                )
            }
        }
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        lagreData(packet)

        "Lagret startdata for event $eventName.".also {
            logger.info(it)
            sikkerLogger.info("$it\n${packet.toPretty()}")
        }

        etterDataLagret(packet, context)
    }

    private fun lagreData(packet: JsonMessage) {
        val melding = packet.toJson().parseJson().toMap()

        val transaksjonId = randomUuid()

        // TODO fjern når client-ID er død
        packet[Key.UUID.str] = transaksjonId.toString()

        melding.plus(
            Key.UUID to transaksjonId.toJson()
        )
            .filterKeys(dataKeys::contains)
            .onEach { (key, data) ->
                redisStore.set(RedisKey.of(transaksjonId, key), data.toString())
            }
    }
}
