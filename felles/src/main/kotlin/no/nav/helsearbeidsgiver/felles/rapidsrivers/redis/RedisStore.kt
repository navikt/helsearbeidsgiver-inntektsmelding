package no.nav.helsearbeidsgiver.felles.rapidsrivers.redis

import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class RedisStore(redisUrl: String) {
    private val sikkerLogger = sikkerLogger()

    private val redisClient = redisUrl.let(RedisClient::create)
    private val connection = redisClient.connect()
    private val syncCommands = connection.sync()

    fun set(key: RedisKey, value: String, ttl: Long = 60L) {
        sikkerLogger.debug("Setting in redis: $key -> $value")
        syncCommands.set(key.toString(), value, SetArgs().ex(ttl))
    }

    fun get(key: RedisKey): String? {
        val value = syncCommands.get(key.toString())
        sikkerLogger.debug("Getting from redis: $key -> $value")
        return value
    }

    fun exist(vararg keys: RedisKey): Long {
        val keysAsString = keys.map { it.toString() }.toTypedArray()
        val count = syncCommands.exists(*keysAsString)
        sikkerLogger.debug("Checking exist in redis: ${keys.contentToString()} -> $count")
        return count
    }

    fun shutdown() {
        connection.close()
        redisClient.shutdown()
    }
}
