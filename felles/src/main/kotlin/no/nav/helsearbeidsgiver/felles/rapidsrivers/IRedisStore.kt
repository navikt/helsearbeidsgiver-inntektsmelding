package no.nav.helsearbeidsgiver.felles.rapidsrivers

interface IRedisStore {
    fun set(key: String, value: String, ttl: Long = 60L)
    fun get(key: String): String?
    fun exist(vararg keys: String): Long
    fun shutdown()
}
