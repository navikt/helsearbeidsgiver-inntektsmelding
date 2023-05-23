package no.nav.helsearbeidsgiver.felles.rapidsrivers

import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs

class RedisStore(redisUrl: String) {
    private val redisClient = redisUrl.let(RedisClient::create)
    private val connection = redisClient.connect()
    private val syncCommands = connection.sync()

    fun set(key: String, value: String, ttl: Long = 60L) {
        syncCommands.set(key, value, SetArgs().ex(ttl))
    }

    fun get(key: String): String? =
        syncCommands.get(key)

    fun exist(vararg keys: String): Long = syncCommands.exists(*keys)

    fun shutdown() {
        connection.close()
        redisClient.shutdown()
    }
}
