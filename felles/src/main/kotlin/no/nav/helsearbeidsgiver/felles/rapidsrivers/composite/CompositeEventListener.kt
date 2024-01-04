package no.nav.helsearbeidsgiver.felles.rapidsrivers.composite

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
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

    abstract fun new(message: JsonMessage)
    abstract fun inProgress(message: JsonMessage)
    abstract fun finalize(message: JsonMessage)
    abstract fun onError(message: JsonMessage, fail: Fail)

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val json = packet.toJson().parseJson().toMap()

        if (Key.FORESPOERSEL_ID.lesOrNull(String.serializer(), json).isNullOrEmpty()) {
            logger.warn("Mangler forespørselId!")
            sikkerLogger.warn("Mangler forespørselId!")
        }

        val transaksjonId = json[Key.UUID]?.fromJson(UuidSerializer)

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
            val fail = toFailOrNull(json)
            if (fail != null) {
                sikkerLogger.error("Feilmelding er '${fail.feilmelding}'. Utløsende melding er \n${fail.utloesendeMelding.toPretty()}")
                return onError(packet, fail)
            }

            val clientIdRedisKey = RedisKey.of(transaksjonId, event)
            val lagretClientId = redisStore.get(clientIdRedisKey)

            return when {
                lagretClientId.isNullOrEmpty() -> {
                    if (!isEventMelding(json)) {
                        "Servicen er inaktiv for gitt event. Mest sannsynlig skyldes dette timeout av Redis-verdier.".also {
                            logger.error(it)
                            sikkerLogger.error(it)
                        }
                        Unit
                    } else {
                        val clientId = json[Key.CLIENT_ID]?.fromJson(UuidSerializer)
                            .orDefault {
                                "Client-ID mangler. Bruker transaksjon-ID som backup.".also {
                                    logger.warn(it)
                                    sikkerLogger.warn(it)
                                }
                                transaksjonId
                            }

                        redisStore.set(clientIdRedisKey, clientId.toString())

                        new(packet)
                    }
                }

                isAllDataCollected(transaksjonId) -> finalize(packet)
                else -> inProgress(packet)
            }
        }
    }

    fun toFailOrNull(json: Map<IKey, JsonElement>): Fail? =
        json[Key.FAIL]
            ?.runCatching {
                fromJson(Fail.serializer())
            }
            ?.getOrNull()

    private fun isEventMelding(json: Map<IKey, JsonElement>): Boolean =
        json[Key.EVENT_NAME] != null &&
            json.keys.intersect(setOf(Key.BEHOV, Key.DATA, Key.FAIL)).isEmpty()

    fun isDataCollected(keys: List<RedisKey>): Boolean =
        redisStore.exist(keys) == keys.size.toLong()

    private fun isAllDataCollected(transaksjonId: UUID): Boolean {
        val allKeys = dataKeys.map { RedisKey.of(transaksjonId, it) }
        val numKeysInRedis = redisStore.exist(allKeys)
        logger.info("found " + numKeysInRedis)
        return numKeysInRedis == dataKeys.size.toLong()
    }
}
