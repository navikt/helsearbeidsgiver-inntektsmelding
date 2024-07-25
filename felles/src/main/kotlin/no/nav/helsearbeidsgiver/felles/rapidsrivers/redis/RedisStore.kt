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
                // TODO slett etter overgangsperiode
                ?: oldGet(key)

        val valueJson = value?.parseJson()

        sikkerLogger.debug("Getting from redis: ${key.toStoreKey()} -> ${valueJson?.toPretty()}")
        return valueJson
    }

    fun getAll(keys: Set<RedisKey>): Map<String, JsonElement> {
        val storeKeys = keys.map { it.toStoreKey() }.toTypedArray()
        return redis
            .getAll(*storeKeys)
            // TODO slett etter overgangsperiode
            .plus(oldGetAll(keys))
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

        // TODO slett etter overgangsperiode
        oldSet(key, value)
    }

    private fun oldGet(key: RedisKey): String? = redis.get(key.toString())

    private fun oldGetAll(keys: Set<RedisKey>): Map<String, String> = redis.getAll(*keys.map { it.toString() }.toTypedArray())

    private fun oldSet(
        key: RedisKey,
        value: JsonElement,
    ) {
        redis.set(key.toString(), value.toString())
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
}
