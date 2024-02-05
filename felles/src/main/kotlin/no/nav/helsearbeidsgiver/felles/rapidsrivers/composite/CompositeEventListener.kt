package no.nav.helsearbeidsgiver.felles.rapidsrivers.composite

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.ModelUtils.toFailOrNull
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.collection.mapKeysNotNull
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
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

    abstract fun new(melding: Map<Key, JsonElement>)
    abstract fun inProgress(melding: Map<Key, JsonElement>)
    abstract fun finalize(melding: Map<Key, JsonElement>)
    abstract fun onError(melding: Map<Key, JsonElement>, fail: Fail)

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val melding = packet.toJson().parseJson().toMap()

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

            val clientIdRedisKey = RedisKey.of(transaksjonId, event)

            val meldingMedRedisData = getAllRedisData(transaksjonId) + melding

            return when {
                fail != null -> {
                    sikkerLogger.error("Feilmelding er '${fail.feilmelding}'. Utløsende melding er \n${fail.utloesendeMelding.toPretty()}")
                    onError(meldingMedRedisData, fail)
                }

                redisStore.get(clientIdRedisKey).isNullOrEmpty() -> {
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

                isAllDataCollected(transaksjonId) -> {
                    finalize(meldingMedRedisData)
                }

                else -> {
                    inProgress(meldingMedRedisData)
                }
            }
        }
    }

    private fun isEventMelding(melding: Map<Key, JsonElement>): Boolean =
        melding[Key.EVENT_NAME] != null &&
            melding.keys.intersect(setOf(Key.BEHOV, Key.DATA, Key.FAIL)).isEmpty()

    fun isDataCollected(keys: Set<RedisKey>): Boolean =
        redisStore.exist(keys) == keys.size.toLong()

    private fun getAllRedisData(transaksjonId: UUID): Map<Key, JsonElement> {
        val allDataKeys = (startKeys + dataKeys).map { RedisKey.of(transaksjonId, it) }.toSet()
        return redisStore.getAll(allDataKeys)
            .mapKeysNotNull { key ->
                key.removePrefix(transaksjonId.toString())
                    .runCatching(Key::fromString)
                    .getOrElse {
                        sikkerLogger.error("Feil med nøkkel i Redis.", it)
                        null
                    }
            }
            .mapValuesNotNull { value ->
                // Midlertidig fiks for strenger som ikke lagres som JSON i Redis.
                runCatching {
                    val json = value.parseJson()

                    // Strenger uten mellomrom parses OK, men klarer ikke leses som streng
                    if (json is JsonPrimitive) {
                        if (
                            json is JsonNull ||
                            json.isString ||
                            json.booleanOrNull != null
                        ) {
                            json
                        } else if (json.intOrNull != null) {
                            "\"${json.int}\"".parseJson()
                        } else if (json.longOrNull != null) {
                            "\"${json.long}\"".parseJson()
                        } else if (json.doubleOrNull != null) {
                            "\"${json.double}\"".parseJson()
                        } else if (json.floatOrNull != null) {
                            "\"${json.float}\"".parseJson()
                        } else {
                            "\"$value\"".parseJson()
                        }
                    } else {
                        json
                    }
                }
                    // Strenger med mellomrom ender her
                    .recoverCatching {
                        sikkerLogger.warn("Klarte ikke parse redis-verdi.\nvalue=$value", it)
                        "\"$value\"".parseJson()
                    }
                    .getOrElse {
                        sikkerLogger.warn("Klarte ikke backup-parse redis-verdi.\nvalue=$value", it)
                        null
                    }
            }
    }

    private fun isAllDataCollected(transaksjonId: UUID): Boolean {
        val allKeys = dataKeys.map { RedisKey.of(transaksjonId, it) }.toSet()
        val numKeysInRedis = redisStore.exist(allKeys)
        logger.info("found " + numKeysInRedis)
        return numKeysInRedis == dataKeys.size.toLong()
    }
}
