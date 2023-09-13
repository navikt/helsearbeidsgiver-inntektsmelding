package no.nav.helsearbeidsgiver.felles.rapidsrivers.redis

import com.fasterxml.jackson.databind.JsonNode
import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import no.nav.helsearbeidsgiver.felles.json.Jackson
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class RedisStore(redisUrl: String) : IRedisStore {
    private val redisClient = redisUrl.let(RedisClient::create)
    private val connection = redisClient.connect()
    private val syncCommands = connection.sync()

    override fun set(key: String, value: String, ttl: Long) {
        sikkerLogger().debug("Setting in redis: $key -> $value")
        syncCommands.set(key, value, SetArgs().ex(ttl))
    }

    override fun set(key: RedisKey, value: String, ttl: Long) {
        sikkerLogger().debug("Setting in redis: $key -> $value")
        syncCommands.set(key.toString(), value, SetArgs().ex(ttl))
    }

    override fun set(key: RedisKey, value: JsonNode, ttl: Long) {
        sikkerLogger().debug("Setting in redis: $key -> $value")
        syncCommands.set(key.toString(), value.toString(), SetArgs().ex(ttl))
    }

    override fun get(key: String): String? {
        val value = syncCommands.get(key)
        sikkerLogger().debug("Getting from redis: $key -> $value")
        return value
    }

    override fun get(key: RedisKey): String? {
        val value = syncCommands.get(key.toString())
        sikkerLogger().debug("Getting from redis: $key -> $value")
        return value
    }

    override fun <T : Any> get(key: RedisKey, clazz: Class<T>): T? {
        val value = syncCommands.get(key.toString())
        return if (value.isNullOrEmpty()) null else Jackson.objectMapper.readValue(value, clazz)
    }

    override fun exist(vararg keys: String): Long {
        val count = syncCommands.exists(*keys)
        sikkerLogger().debug("Checking exist in redis: ${keys.contentToString()} -> $count")
        return count
    }

    override fun exist(vararg keys: RedisKey): Long {
        val count = syncCommands.exists(*keys.map { it.toString() }.toTypedArray())
        sikkerLogger().debug("Checking exist in redis: ${keys.contentToString()} -> $count")
        return count
    }

    override fun shutdown() {
        connection.close()
        redisClient.shutdown()
    }
}
