package no.nav.hag.simba.utils.rr.service

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.json.krev
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.json.toPretty
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
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
                    service.redisStore.skrivMellomlagring(kontekstId, key, data)
                }

                "Lagret ${dataMap.size} nøkler (med data) i Redis.".also {
                    logger.info(it)
                    sikkerLogger.info("$it\n${json.toPretty()}")
                }

                val mellomlagrede = service.redisStore.lesAlleMellomlagrede(kontekstId)
                val utvidetDataMap = mellomlagrede.plus(dataMap).toJson()
                val meldingMedUtvidetData = json.plus(Key.DATA to utvidetDataMap)
                val meldingMedRedisData = mellomlagrede.plus(meldingMedUtvidetData)

                service.onData(meldingMedRedisData)
            }

            is FailMelding -> {
                "Feilmelding er '${fail.feilmelding}'.".also {
                    logger.error(it)
                    sikkerLogger.error("$it Utløsende melding er \n${fail.utloesendeMelding.toPretty()}")
                }

                val mellomlagrede = service.redisStore.lesAlleMellomlagrede(kontekstId)

                val meldingMedRedisData =
                    mellomlagrede.plus(
                        mapOf(
                            Key.EVENT_NAME to eventName.toJson(),
                            Key.KONTEKST_ID to kontekstId.toJson(),
                            Key.DATA to mellomlagrede.toJson(),
                        ),
                    )

                service.onError(meldingMedRedisData, fail)
            }
        }

        return null
    }
}

sealed class ServiceRiver : ObjectRiver.Simba<ServiceMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    abstract val service: Service

    final override fun les(json: Map<Key, JsonElement>): ServiceMelding? =
        when {
            Key.FAIL in json -> {
                val fail = Key.FAIL.les(Fail.serializer(), json)

                FailMelding(
                    eventName = Key.EVENT_NAME.krev(service.eventName, EventName.serializer(), fail.utloesendeMelding),
                    kontekstId = Key.KONTEKST_ID.krev(fail.kontekstId, UuidSerializer, fail.utloesendeMelding),
                    fail = fail,
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
                        kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                        dataMap = nestedData,
                    )
                } else {
                    null
                }
            }
        }

    // Servicer publiserer ikke via ObjectRiver, så denne nøkkelen blir ikke brukt
    final override fun ServiceMelding.bestemNoekkel(): KafkaKey = KafkaKey(UUID.randomUUID())

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
                        Log.kontekstId(kontekstId),
                    )

                is FailMelding ->
                    mapOf(
                        Log.event(eventName),
                        Log.kontekstId(kontekstId),
                    )
            },
        )
}
