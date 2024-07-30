package no.nav.helsearbeidsgiver.felles.rapidsrivers.redis

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class RedisStore(
// class RedisStore<Success : Any, Failure : Any>(
    private val redis: RedisConnection,
    private val keyPrefix: RedisPrefix,
//    private val successSerializer: KSerializer<Success>,
//    private val failureSerializer: KSerializer<Failure>,
) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    internal val keyPartSeparator = "#"

    fun get(key: RedisKey): JsonElement? {
        val value =
            redis.get(key.toStoreKey())

        val valueJson = value?.parseJson()

        sikkerLogger.debug("Getting from redis: ${key.toStoreKey()} -> ${valueJson?.toPretty()}")
        return valueJson
    }

    fun getAll(keys: Set<RedisKey>): Map<String, JsonElement> {
        val storeKeys = keys.map { it.toStoreKey() }.toTypedArray()
        return redis
            .getAll(*storeKeys)
            .mapValuesNotNull { value ->
                value
                    .runCatching(String::parseJson)
                    .getOrElse { error ->
                        "Klarte ikke parse redis-verdi.".also {
                            logger.error(it)
                            sikkerLogger.error("$it\nvalue=$value", error)
                        }
                        null
                    }
            }.mapKeys {
                it.key.removePrefix("${keyPrefix.name}$keyPartSeparator")
            }.also {
                sikkerLogger.debug("Getting all from redis: $it")
            }
    }

    fun set(
        key: RedisKey,
        value: JsonElement,
    ) {
        sikkerLogger.debug("Setting in redis: ${key.toStoreKey()} -> ${value.toPretty()}")

        redis.set(key.toStoreKey(), value.toString())
    }

    private fun RedisKey.toStoreKey(): String = listOf(keyPrefix.name).plus(keyParts()).joinToString(separator = keyPartSeparator)
}

enum class RedisPrefix {
    AktiveOrgnr,
    HentForespoersel,
    HentSelvbestemtIm,
    Innsending,
    InntektSelvbestemt,
    Inntekt,
    Kvittering,
    LagreSelvbestemtIm,
    ManuellOpprettSak,
    OpprettOppgave,
    OpprettSak,
    TilgangForespoersel,
    TilgangOrg,
    BerikInntektsmeldingService,
}
