package no.nav.helsearbeidsgiver.felles.redis

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull

class RedisConnection(
    host: String,
    port: Int,
    username: String,
    password: String,
) {
    private val client: RedisClient =
        RedisURI
            .builder()
            .withSsl(true)
            .withHost(host)
            .withPort(port)
            .withAuthentication(username, password)
            .withDatabase(0)
            .build()
            .let(RedisClient::create)
    private val connection: StatefulRedisConnection<String, String> = client.connect()
    private val syncCommands: RedisCommands<String, String> = connection.sync()

    fun get(key: String): String? = syncCommands.get(key)

    internal fun getAll(keys: List<String>): Map<String, String> {
        val keysAsArray = keys.toSet().toTypedArray()
        return syncCommands
            .mget(*keysAsArray)
            .associate { it.key to it.getValueOrElse(null) }
            .mapValuesNotNull { it }
    }

    internal fun set(
        key: String,
        value: String,
    ) {
        syncCommands.setex(key, 60L, value)
    }

    fun close() {
        connection.close()
        client.shutdown()
    }
}
