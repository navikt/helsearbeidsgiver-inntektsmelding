package no.nav.helsearbeidsgiver.felles.rapidsrivers

interface IStore {
    fun set(key: String, value: String, ttl: Long = 60L)
    fun get(key: String): String?
    fun exist(vararg keys: String): Long
    fun shutdown()
}
