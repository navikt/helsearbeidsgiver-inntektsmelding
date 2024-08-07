package no.nav.helsearbeidsgiver.felles.rapidsrivers.service

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.json.toPretty
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.collection.mapKeysNotNull
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class ServiceRiverStateless(
    override val service: Service,
) : ServiceRiver() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun ServiceMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        when (this) {
            is DataMelding -> {
                service.onData(dataMap + json)
            }

            is FailMelding -> {
                "Feilmelding er '${fail.feilmelding}'.".also {
                    logger.error(it)
                    sikkerLogger.error("$it Utløsende melding er \n${fail.utloesendeMelding.toPretty()}")
                }

                service.onError(json, fail)
            }
        }

        return null
    }
}

class ServiceRiverStateful<S>(
    override val service: S,
) : ServiceRiver() where S : Service, S : Service.MedRedis {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun ServiceMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        when (this) {
            is DataMelding -> {
                dataMap.forEach { (key, data) ->
                    service.redisStore.set(RedisKey.of(transaksjonId, key), data)
                }

                "Lagret ${dataMap.size} nøkler (med data) i Redis.".also {
                    logger.info(it)
                    sikkerLogger.info("$it\n${json.toPretty()}")
                }

                val meldingMedRedisData = getAllRedisData(transaksjonId) + json

                service.onData(meldingMedRedisData)
            }

            is FailMelding -> {
                "Feilmelding er '${fail.feilmelding}'.".also {
                    logger.error(it)
                    sikkerLogger.error("$it Utløsende melding er \n${fail.utloesendeMelding.toPretty()}")
                }

                val meldingMedRedisData = getAllRedisData(transaksjonId) + json

                service.onError(meldingMedRedisData, fail)
            }
        }

        return null
    }

    private fun getAllRedisData(transaksjonId: UUID): Map<Key, JsonElement> {
        val allKeys = Key.entries.map { RedisKey.of(transaksjonId, it) }.toSet()
        return service.redisStore
            .getAll(allKeys)
            .mapKeysNotNull { key ->
                key
                    .removePrefix("$transaksjonId${service.redisStore.keyPartSeparator}")
                    .runCatching(Key::fromString)
                    .getOrElse { error ->
                        "Feil med nøkkel '$key' i Redis.".also {
                            logger.error(it)
                            sikkerLogger.error(it, error)
                        }
                        null
                    }
            }
    }
}

sealed class ServiceRiver : ObjectRiver<ServiceMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    abstract val service: Service

    final override fun les(json: Map<Key, JsonElement>): ServiceMelding? =
        when {
            Key.FAIL in json -> {
                FailMelding(
                    eventName = Key.EVENT_NAME.krev(service.eventName, EventName.serializer(), json),
                    transaksjonId = Key.UUID.les(UuidSerializer, json),
                    fail = Key.FAIL.les(Fail.serializer(), json),
                )
            }

            // Meldinger med behov stammer fra servicen selv
            Key.BEHOV in json -> {
                null
            }

            else -> {
                val nestedData = json[Key.DATA]?.toMap()
                if (nestedData != null) {
                    DataMelding(
                        eventName = Key.EVENT_NAME.krev(service.eventName, EventName.serializer(), json),
                        transaksjonId = Key.UUID.les(UuidSerializer, json),
                        dataMap = nestedData,
                    )
                } else {
                    null
                }
            }
        }

    final override fun ServiceMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement>? {
        val feilmelding =
            when (this) {
                is DataMelding ->
                    "Noe gikk galt under håndtering av melding med data."

                is FailMelding ->
                    "Noe gikk galt under håndtering av melding med feil."
            }

        logger.error(feilmelding)
        sikkerLogger.error("$feilmelding\n${json.toPretty()}", error)

        return null
    }

    final override fun ServiceMelding.loggfelt(): Map<String, String> =
        mapOf(Log.klasse(service)).plus(
            when (this) {
                is DataMelding ->
                    mapOf(
                        Log.event(eventName),
                        Log.transaksjonId(transaksjonId),
                    )

                is FailMelding ->
                    mapOf(
                        Log.event(eventName),
                        Log.transaksjonId(transaksjonId),
                    )
            },
        )
}
