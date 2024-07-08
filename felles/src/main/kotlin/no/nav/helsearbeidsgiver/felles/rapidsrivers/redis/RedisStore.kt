package no.nav.helsearbeidsgiver.felles.rapidsrivers.redis

import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class RedisStore(redisUrl: String) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    private val redisClient = redisUrl.let(RedisClient::create)
    private val connection = redisClient.connect()
    private val syncCommands = connection.sync()

    fun set(
        key: RedisKey,
        value: String,
        ttl: Long = 60L,
    ) {
        sikkerLogger.debug("Setting in redis: $key -> $value")
        syncCommands.set(key.toString(), value, SetArgs().ex(ttl))
    }

    fun get(key: RedisKey): String? {
        val value = syncCommands.get(key.toString())
        sikkerLogger.debug("Getting from redis: $key -> $value")
        return value
    }

    fun getAll(keys: Set<RedisKey>): Map<String, String> {
        val keysAsString = keys.map { it.toString() }.toTypedArray()
        return syncCommands.mget(*keysAsString)
            .associate { it.key to it.getValueOrElse(null) }
            .mapValuesNotNull { it }
            .also {
                sikkerLogger.debug("Getting all from redis: $it")
            }
    }

    fun shutdown() {
        logger.info("Stoppsignal mottatt, lukker Redis-tilkobling.")
        connection.close()
        redisClient.shutdown()
    }
}
