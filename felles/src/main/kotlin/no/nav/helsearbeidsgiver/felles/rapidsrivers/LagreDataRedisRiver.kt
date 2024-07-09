package no.nav.helsearbeidsgiver.felles.rapidsrivers

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.json.toPretty
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class LagreDataRedisRiver(
    private val event: EventName,
    private val dataKeys: Set<Key>,
    private val rapid: RapidsConnection,
    private val redisStore: RedisStore,
    private val etterDataLagret: (JsonMessage, MessageContext) -> Unit,
) : River.PacketListener {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    init {
        River(rapid)
            .validate {
                it.demandValues(Key.EVENT_NAME to event.name)
                it.demandKeys(Key.DATA)

                it.rejectKeys(Key.BEHOV)

                it.requireKeys(Key.UUID)

                it.interestedIn(
                    Key.FORESPOERSEL_ID,
                    *dataKeys.toTypedArray(),
                )
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        val melding = packet.toJson().parseJson().toMap()

        if (Key.FORESPOERSEL_ID.lesOrNull(String.serializer(), melding).isNullOrEmpty()) {
            logger.warn("Mangler forespørselId!")
            sikkerLogger.warn("Mangler forespørselId!")
        }

        val transaksjonId =
            runCatching {
                Key.UUID.lesOrNull(UuidSerializer, melding)
            }.onFailure {
                sikkerLogger.error("Klarte ikke lese transaksjon-ID.", it)
            }.getOrNull()

        if (transaksjonId != null) {
            MdcUtils.withLogFields(
                Log.klasse(this),
                Log.event(event),
                Log.transaksjonId(transaksjonId),
            ) {
                val antallLagret = lagreData(melding, transaksjonId)

                if (antallLagret > 0) {
                    sikkerLogger.info("Lagret $antallLagret nøkler (med data) i Redis for melding\n${melding.toPretty()}")
                    // TODO packet -> melding
                    etterDataLagret(packet, rapid)
                } else {
                    sikkerLogger.warn("Fant ikke data å lagre for melding\n${melding.toPretty()}")
                }
            }
        } else {
            sikkerLogger.error("Transaksjon-ID er ikke initialisert for melding\n${melding.toPretty()}")
        }
    }

    private fun lagreData(
        melding: Map<Key, JsonElement>,
        transaksjonId: UUID,
    ): Int =
        melding
            .filterKeys(dataKeys::contains)
            .onEach { (key, data) ->
                redisStore.set(RedisKey.of(transaksjonId, key), data.toString())
            }.size
}
