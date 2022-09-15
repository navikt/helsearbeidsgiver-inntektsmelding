package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.lettuce.core.RedisClient
import kotlinx.coroutines.delay
import java.util.concurrent.TimeoutException

class RedisPoller(val redisClient: RedisClient) {

    suspend fun getValue(key: String, maxRetries: Int = 10, waitMillis: Long = 500): String {
        for (x in 0..maxRetries) {
            val value = getValue(key)
            if (value.isNullOrEmpty()) {
                delay(waitMillis)
            } else {
                return value
            }
        }
        throw TimeoutException("Klarte ikke hente ut verdier!")
    }

    private fun getValue(key: String): String? {
        redisClient.connect().use {
            return it.sync().get(key)
        }
    }

    fun shutdown() {
        redisClient.shutdown()
    }
}
