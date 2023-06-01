package no.nav.helsearbeidsgiver.felles.test.mock

import no.nav.helsearbeidsgiver.felles.rapidsrivers.IRedisStore

class MockRedisStore : IRedisStore {

    val store = HashMap<String, String>()

    override fun set(key: String, value: String, ttl: Long) {
        store.put(key, value)
    }

    override fun get(key: String): String? {
        return store.get(key)
    }

    override fun exist(vararg keys: String): Long {
        TODO("Not yet implemented")
    }

    override fun shutdown() {
        TODO("Not yet implemented")
    }
}
