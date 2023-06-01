package no.nav.helsearbeidsgiver.felles.test.mock

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
class MockRedisStore : IRedisStore {

    val store = HashMap<String, String>()

    override fun set(key: String, value: String, ttl: Long) {
        store.put(key, value)
    }

    override fun set(key: RedisKey, value: String, ttl: Long) {
        TODO("Not yet implemented")
    }

    override fun set(key: RedisKey, value: JsonNode, ttl: Long) {
        TODO("Not yet implemented")
    }

    override fun get(key: String): String? {
        return store.get(key)
    }

    override fun get(key: RedisKey): String? {
        TODO("Not yet implemented")
    }

    override fun <T : Any> get(key: RedisKey, clazz: Class<T>): T? {
        TODO("Not yet implemented")
    }

    override fun exist(vararg keys: String): Long {
        TODO("Not yet implemented")
    }

    override fun exist(vararg keys: RedisKey): Long {
        TODO("Not yet implemented")
    }

    override fun shutdown() {
        TODO("Not yet implemented")
    }
}
