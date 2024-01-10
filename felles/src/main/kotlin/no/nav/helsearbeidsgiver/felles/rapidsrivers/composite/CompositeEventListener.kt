package no.nav.helsearbeidsgiver.felles.rapidsrivers.composite

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.ModelUtils.Companion.toFailOrNull
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.util.UUID

abstract class CompositeEventListener : River.PacketListener {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    abstract val redisStore: RedisStore
    abstract val event: EventName
    abstract val startKeys: List<Key>
    abstract val dataKeys: List<Key>

    abstract fun new(melding: Map<Key, JsonElement>)
    abstract fun inProgress(melding: Map<Key, JsonElement>)
    abstract fun finalize(melding: Map<Key, JsonElement>)
    abstract fun onError(melding: Map<Key, JsonElement>, fail: Fail)

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val melding = packet.toJson().parseJson().fromJsonMapFiltered(Key.serializer())

        if (Key.FORESPOERSEL_ID.lesOrNull(String.serializer(), melding).isNullOrEmpty()) {
            logger.warn("Mangler forespørselId!")
            sikkerLogger.warn("Mangler forespørselId!")
        }

        val transaksjonId = melding[Key.UUID]?.fromJson(UuidSerializer)

        if (transaksjonId == null) {
            "Melding mangler transaksjon-ID. Ignorerer melding.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
            return
        }

        MdcUtils.withLogFields(
            Log.transaksjonId(transaksjonId)
        ) {
            val fail = toFailOrNull(melding)
            if (fail != null) {
                sikkerLogger.error("Feilmelding er '${fail.feilmelding}'. Utløsende melding er \n${fail.utloesendeMelding.toPretty()}")
                return onError(melding, fail)
            }

            val clientIdRedisKey = RedisKey.of(transaksjonId, event)
            val lagretClientId = redisStore.get(clientIdRedisKey)

            return when {
                lagretClientId.isNullOrEmpty() -> {
                    if (!isEventMelding(melding)) {
                        "Servicen er inaktiv for gitt event. Mest sannsynlig skyldes dette timeout av Redis-verdier.".also {
                            logger.error(it)
                            sikkerLogger.error(it)
                        }
                        Unit
                    } else {
                        val clientId = melding[Key.CLIENT_ID]?.fromJson(UuidSerializer)
                            .orDefault {
                                "Client-ID mangler. Bruker transaksjon-ID som backup.".also {
                                    logger.warn(it)
                                    sikkerLogger.warn(it)
                                }
                                transaksjonId
                            }

                        redisStore.set(clientIdRedisKey, clientId.toString())

                        new(melding)
                    }
                }

                isAllDataCollected(transaksjonId) -> finalize(melding)
                else -> inProgress(melding)
            }
        }
    }

    private fun isEventMelding(melding: Map<Key, JsonElement>): Boolean =
        melding[Key.EVENT_NAME] != null &&
            melding.keys.intersect(setOf(Key.BEHOV, Key.DATA, Key.FAIL)).isEmpty()

    fun isDataCollected(keys: List<RedisKey>): Boolean =
        redisStore.exist(keys) == keys.size.toLong()

    private fun isAllDataCollected(transaksjonId: UUID): Boolean {
        val allKeys = dataKeys.map { RedisKey.of(transaksjonId, it) }
        val numKeysInRedis = redisStore.exist(allKeys)
        logger.info("found " + numKeysInRedis)
        return numKeysInRedis == dataKeys.size.toLong()
    }
}
