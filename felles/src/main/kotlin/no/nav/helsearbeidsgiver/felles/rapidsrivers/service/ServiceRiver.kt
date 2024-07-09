package no.nav.helsearbeidsgiver.felles.rapidsrivers.service

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.json.toPretty
import no.nav.helsearbeidsgiver.felles.loeser.ObjectRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.collection.mapKeysNotNull
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class ServiceRiver(
    private val service: Service,
) : ObjectRiver<ServiceMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): ServiceMelding? {
        val nestedData =
            json[Key.DATA]
                ?.runCatching { toMap() }
                ?.getOrNull()
                .orEmpty()

        return when {
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

            // Støtter Key.DATA som flagg med datafelt på rot (metode på vei ut)
            Key.DATA in json &&
                (
                    service.startKeys.all(json::containsKey) ||
                        service.dataKeys.any(json::containsKey)
                ) -> {
                DataMelding(
                    eventName = Key.EVENT_NAME.krev(service.eventName, EventName.serializer(), json),
                    transaksjonId = Key.UUID.les(UuidSerializer, json),
                    dataMap =
                        json.filterKeys {
                            (service.startKeys + service.dataKeys).contains(it)
                        },
                )
            }

            // Støtter Key.DATA som objekt som inneholder datafelt (metode på vei inn)
            // TODO Når all data er nested under Key.DATA så kan startKeys og dataKeys få visibility protected
            service.startKeys.all(nestedData::containsKey) ||
                service.dataKeys.any(nestedData::containsKey) -> {
                DataMelding(
                    eventName = Key.EVENT_NAME.krev(service.eventName, EventName.serializer(), json),
                    transaksjonId = Key.UUID.les(UuidSerializer, json),
                    dataMap =
                        nestedData.filterKeys {
                            (service.startKeys + service.dataKeys).contains(it)
                        },
                )
            }

            else -> {
                null
            }
        }
    }

    override fun ServiceMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        when (this) {
            is DataMelding -> haandterData(json)
            is FailMelding -> haandterFail(json)
        }

        return null
    }

    override fun ServiceMelding.haandterFeil(
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

    override fun ServiceMelding.loggfelt(): Map<String, String> =
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

    private fun DataMelding.haandterData(melding: Map<Key, JsonElement>) {
        dataMap.forEach { (key, data) ->
            service.redisStore.set(RedisKey.of(transaksjonId, key), data)
        }

        // if-sjekk trengs trolig ikke, men beholder midlertidig for sikkerhets skyld
        if (dataMap.isNotEmpty()) {
            "Lagret ${dataMap.size} nøkler (med data) i Redis.".also {
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

    private fun berikMedRedisData(
        melding: Map<Key, JsonElement>,
        transaksjonId: UUID,
    ): Map<Key, JsonElement>? {
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
                    .removePrefix(service.redisStore.keyPartSeparator)
                    // TODO erstatter de to foregående 'removePrefix' etter overgangsperiode
//                    .removePrefix("$transaksjonId${service.redisStore.keyPartSeparator}")
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
