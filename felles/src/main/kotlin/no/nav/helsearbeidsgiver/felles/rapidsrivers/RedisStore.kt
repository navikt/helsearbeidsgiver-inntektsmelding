package no.nav.helsearbeidsgiver.felles.rapidsrivers

import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import no.nav.helsearbeidsgiver.utils.log.logger

class RedisStore(redisUrl: String) {
    private val redisClient = redisUrl.let(RedisClient::create)
    private val connection = redisClient.connect()
    private val syncCommands = connection.sync()

    fun set(key: String, value: String, ttl: Long = 60L) {
        logger().debug("Setting in redis: $key -> $value")
        syncCommands.set(key, value, SetArgs().ex(ttl))
    }

    fun get(key: String): String? {
        val value = syncCommands.get(key)
        logger().debug("Getting from redis: $key -> $value")
        return value
    }

    fun exist(vararg keys: String): Long {
        val count = syncCommands.exists(*keys)
        logger().debug("Checking exist in redis: $keys -> $count")
        return count
    }

    fun shutdown() {
        connection.close()
        redisClient.shutdown()
    }
}
