package no.nav.helsearbeidsgiver.felles.test.mock

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helsearbeidsgiver.felles.json.Jackson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
class MockRedisStore : IRedisStore {

    val store = HashMap<String, String>()

    override fun set(key: String, value: String, ttl: Long) {
        println("Setter inn: $key -> $value")
        if (key.endsWith("uuid")) {
            println("Setter inn uuid $value")
            store.put("uuid", value) // CollectData() lager *ny* UUID så i test må vi finne den...
            // denne funker bare når uuid er lagt inn i service sine datafelter
        }
        if (key.endsWith("forespoerselId")) {
            println("Setter inn uuid")
            store.put("uuid", key.substring(0, key.indexOf("forespoerselId"))) // CollectData() lager *ny* UUID så i test må vi finne den...
        }
        store.put(key, value)
    }

    override fun set(key: RedisKey, value: String, ttl: Long) {
        println("Setter inn: $key -> $value")
        store.put(key.toString(), value)
    }

    override fun set(key: RedisKey, value: JsonNode, ttl: Long) {
        TODO("Not yet implemented")
    }

    override fun get(key: String): String? {
        return store.get(key)
    }

    override fun get(key: RedisKey): String? {
        println("Henter: $key")
        return store.get(key.toString())
    }

    override fun <T : Any> get(key: RedisKey, clazz: Class<T>): T? {
        val value = get(key)
        return if (value.isNullOrEmpty()) null else Jackson.objectMapper.readValue(value, clazz)
    }

    override fun exist(vararg keys: String): Long {
        val s =
            keys.filter {
                store.containsKey(it)
            }.size.toLong()
        return s
    }

    override fun exist(vararg keys: RedisKey): Long {
        val s = keys.filter {
            store.containsKey(it.toString())
        }.size.toLong()
        return s
    }

    override fun shutdown() {
        TODO("Not yet implemented")
    }
}
