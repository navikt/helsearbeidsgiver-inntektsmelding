package no.nav.helsearbeidsgiver.felles.rapidsrivers.redis

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull

class RedisConnection(
    redisUrl: String
) {
    private val client: RedisClient = redisUrl.let(RedisClient::create)
    private val connection: StatefulRedisConnection<String, String> = client.connect()
    private val syncCommands: RedisCommands<String, String> = connection.sync()

    fun get(key: String): String? =
        syncCommands.get(key)

    internal fun getAll(vararg keys: String): Map<String, String> =
        syncCommands.mget(*keys)
            .associate { it.key to it.getValueOrElse(null) }
            .mapValuesNotNull { it }

    internal fun set(key: String, value: String) {
        syncCommands.setex(key, 60L, value)
    }

    fun close() {
        connection.close()
        client.shutdown()
    }
}
