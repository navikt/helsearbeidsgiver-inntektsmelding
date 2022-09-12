package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs

class RedisClient(redisUrl: String) {
    private val redisClient = RedisClient.create("redis://$redisUrl:6379/0")
    private val connection = redisClient.connect()
    private val syncCommands = connection.sync()

    fun set(key: String, value: String, ttl: Long = 60L) {
        syncCommands.set(key, value, SetArgs().ex(ttl))
    }

    fun get(key: String): String? {
        return syncCommands.get(key)
    }

    fun shutdown() {
        connection.close()
        redisClient.shutdown()
    }
}
