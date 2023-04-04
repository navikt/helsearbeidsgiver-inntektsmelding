package no.nav.helsearbeidsgiver.inntektsmelding.api.cache

import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class LocalCache<T>(
    private val entryDuration: Duration,
    private val maxEntries: Int
) {
    init {
        require(maxEntries > 0) { "Parameter `maxEntries` must be greater than 0, but was $maxEntries." }
    }

    private val cache = mutableMapOf<String, Entry<T>>()

    suspend fun get(key: String, default: suspend () -> T): T =
        cache[key]
            ?.takeIf { it.isNotExpired() }
            ?.value
            ?: put(key, default())

    private fun put(key: String, value: T): T {
        while (cache.size >= maxEntries) {
            removeEntryExpiringEarliest()
        }

        cache[key] = Entry(
            value,
            LocalDateTime.now().plus(entryDuration.toJavaDuration())
        )

        return value
    }

    private fun removeEntryExpiringEarliest() {
        cache.minByOrNull { it.value.expiresAt }
            ?.also { cache.remove(it.key) }
    }
}

suspend fun <T> LocalCache<T>?.getIfCacheNotNull(key: String, default: suspend () -> T): T =
    if (this != null) {
        get(key, default)
    } else {
        default()
    }

private data class Entry<T>(
    val value: T,
    val expiresAt: LocalDateTime
) {
    fun isNotExpired(): Boolean =
        expiresAt.isAfter(LocalDateTime.now())
}
