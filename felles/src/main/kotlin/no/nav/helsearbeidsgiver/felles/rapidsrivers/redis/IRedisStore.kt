package no.nav.helsearbeidsgiver.felles.rapidsrivers.redis

import com.fasterxml.jackson.databind.JsonNode

interface IRedisStore {
    fun set(key: String, value: String, ttl: Long = 60L)
    fun set(key: RedisKey, value: String, ttl: Long = 60L)
    fun set(key: RedisKey, value: JsonNode, ttl: Long = 60L)
    fun get(key: String): String?
    fun get(key: RedisKey): String?
    fun <T : Any> get(key: RedisKey, clazz: Class<T>): T?
    fun exist(vararg keys: String): Long
    fun shutdown()
}
