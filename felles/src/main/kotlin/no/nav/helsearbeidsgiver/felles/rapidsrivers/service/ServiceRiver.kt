package no.nav.helsearbeidsgiver.felles.rapidsrivers.service

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toPretty
import no.nav.helsearbeidsgiver.felles.loeser.ObjectRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.collection.mapKeysNotNull
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.util.UUID

class ServiceRiver(
    private val service: Service
) : ObjectRiver<ServiceMelding>() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): ServiceMelding? =
        when {
            Key.FAIL in json -> {
                FailMelding(
                    eventName = Key.EVENT_NAME.krev(service.eventName, EventName.serializer(), json),
                    transaksjonId = Key.UUID.les(UuidSerializer, json),
                    fail = Key.FAIL.les(Fail.serializer(), json)
                )
            }

            Key.DATA in json &&
                service.dataKeys.any(json::containsKey) -> {
                DataMelding(
                    eventName = Key.EVENT_NAME.krev(service.eventName, EventName.serializer(), json),
                    transaksjonId = Key.UUID.les(UuidSerializer, json),
                    dataMap = json.filterKeys(service.dataKeys::contains)
                )
            }

            setOf(Key.BEHOV, Key.DATA).none(json::containsKey) &&
                service.startKeys.all(json::containsKey) -> {
                // TODO les fra melding når client-ID er død
                val transaksjonId = randomUuid()

                StartMelding(
                    eventName = Key.EVENT_NAME.krev(service.eventName, EventName.serializer(), json),
                    clientId = Key.CLIENT_ID.lesOrNull(UuidSerializer, json),
                    transaksjonId = transaksjonId,
                    startDataMap = json.plus(Key.UUID to transaksjonId.toJson())
                        .filterKeys(service.startKeys::contains)
                )
            }

            else -> {
                null
            }
        }

    override fun ServiceMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        when (this) {
            is StartMelding -> {
                val jsonMedNyTransaksjonId = json.plus(Key.UUID to transaksjonId.toJson())
                haandterStart(jsonMedNyTransaksjonId)
            }

            is DataMelding -> haandterData(json)
            is FailMelding -> haandterFail(json)
        }

        return null
    }

    override fun ServiceMelding.haandterFeil(json: Map<Key, JsonElement>, error: Throwable): Map<Key, JsonElement>? {
        val feilmelding = when (this) {
            is StartMelding ->
                "Noe gikk galt under håndtering av melding som starter service."

            is DataMelding ->
                "Noe gikk galt under håndtering av melding med data."

            is FailMelding ->
                "Noe gikk galt under håndtering av melding med feil."
        }

        logger.error(feilmelding)
        sikkerLogger.error("$feilmelding\n${json.toPretty()}", error)

        return null
    }

    override fun ServiceMelding.loggfelt(): Map<String, String> =
        mapOf(Log.klasse(service)).plus(
            when (this) {
                is StartMelding ->
                    mapOf(
                        Log.event(eventName),
                        Log.transaksjonId(transaksjonId)
                    )

                is DataMelding ->
                    mapOf(
                        Log.event(eventName),
                        Log.transaksjonId(transaksjonId)
                    )

                is FailMelding ->
                    mapOf(
                        Log.event(eventName),
                        Log.transaksjonId(transaksjonId)
                    )
            }
        )

    private fun StartMelding.haandterStart(melding: Map<Key, JsonElement>) {
        startDataMap.forEach { (key, data) ->
            service.redisStore.set(RedisKey.of(transaksjonId, key), data.toString())
        }

        "Lagret startdata for event ${service.eventName}.".also {
            logger.info(it)
            sikkerLogger.info("$it\n${melding.toPretty()}")
        }

        val clientIdRedisKey = RedisKey.of(transaksjonId, service.eventName)

        // if-sjekk trengs trolig ikke, men beholder midlertidig for sikkerhets skyld
        if (service.redisStore.get(clientIdRedisKey).isNullOrEmpty()) {
            val clientId = clientId.orDefault {
                "Client-ID mangler. Bruker transaksjon-ID som backup.".also {
                    logger.warn(it)
                    sikkerLogger.warn(it)
                }
                transaksjonId
            }

            service.redisStore.set(clientIdRedisKey, clientId.toJson().toString())

            service.onStart(melding)
        } else {
            "Client-ID eksisterte fra før. Dette skal aldri skje.".also {
                logger.error(it)
                sikkerLogger.error("$it\n${melding.toPretty()}")
            }
        }
    }

    private fun DataMelding.haandterData(melding: Map<Key, JsonElement>) {
        val antallLagret = dataMap.onEach { (key, data) ->
            service.redisStore.set(RedisKey.of(transaksjonId, key), data.toString())
        }
            .size

        // if-sjekk trengs trolig ikke, men beholder midlertidig for sikkerhets skyld
        if (antallLagret > 0) {
            "Lagret $antallLagret nøkler (med data) i Redis.".also {
                logger.info(it)
                sikkerLogger.info("$it\n${melding.toPretty()}")
            }

            val meldingMedRedisData = berikMedRedisData(melding, transaksjonId)
            if (meldingMedRedisData != null) {
                service.onData(meldingMedRedisData)
            }
        } else {
            "Fant ikke data å lagre.".also {
                logger.error(it)
                sikkerLogger.error("$it\n${melding.toPretty()}")
            }
        }
    }

    private fun FailMelding.haandterFail(melding: Map<Key, JsonElement>) {
        "Feilmelding er '${fail.feilmelding}'.".also {
            logger.error(it)
            sikkerLogger.error("$it Utløsende melding er \n${fail.utloesendeMelding.toPretty()}")
        }

        val meldingMedRedisData = berikMedRedisData(melding, transaksjonId)
        if (meldingMedRedisData != null) {
            service.onError(meldingMedRedisData, fail)
        }
    }

    private fun berikMedRedisData(melding: Map<Key, JsonElement>, transaksjonId: UUID): Map<Key, JsonElement>? {
        val redisData = getAllRedisData(transaksjonId)

        return if (service.isInactive(redisData)) {
            "Service er inaktiv pga. Redis-timeout ('startKeys' mangler).".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
            null
        } else {
            redisData + melding
        }
    }

    private fun getAllRedisData(transaksjonId: UUID): Map<Key, JsonElement> {
        val allDataKeys = (service.startKeys + service.dataKeys).map { RedisKey.of(transaksjonId, it) }.toSet()
        return service.redisStore.getAll(allDataKeys)
            .mapKeysNotNull { key ->
                key.removePrefix(transaksjonId.toString())
                    .runCatching(Key::fromString)
                    .getOrElse { error ->
                        "Feil med nøkkel '$key' i Redis.".also {
                            logger.error(it)
                            sikkerLogger.error(it, error)
                        }
                        null
                    }
            }
            .mapValuesNotNull { value ->
                runCatching {
                    value.parseJson()
                }
                    .getOrElse { error ->
                        "Klarte ikke parse redis-verdi.".also {
                            logger.error(it)
                            sikkerLogger.error("$it\nvalue=$value", error)
                        }
                        null
                    }
            }
    }
}
