package no.nav.helsearbeidsgiver.felles.rapidsrivers

import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class RedisStore(redisUrl: String) : IRedisStore {
    private val redisClient = redisUrl.let(RedisClient::create)
    private val connection = redisClient.connect()
    private val syncCommands = connection.sync()

    override fun set(key: String, value: String, ttl: Long) {
        sikkerLogger().debug("Setting in redis: $key -> $value")
        syncCommands.set(key, value, SetArgs().ex(ttl))
    }

    override fun get(key: String): String? {
        val value = syncCommands.get(key)
        sikkerLogger().debug("Getting from redis: $key -> $value")
        return value
    }

    override fun exist(vararg keys: String): Long {
        val count = syncCommands.exists(*keys)
        sikkerLogger().debug("Checking exist in redis: ${keys.contentToString()} -> $count")
        return count
    }

    override fun shutdown() {
        connection.close()
        redisClient.shutdown()
    }
}
