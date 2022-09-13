package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.delay

class RedisPoller(val redisClient: RedisClient) {
    private var connection: StatefulRedisConnection<String, String>

    init {
        connection = redisClient.connect()
    }

    suspend fun getValue(key: String, maxRetries: Int = 10, waitMillis: Long = 10) : String {
        for (x in 0..maxRetries) {
            val value = getValue(key)
            if (value == null || value == "") {
                delay(waitMillis)
            } else {
                return value
            }
        }
        throw TimeoutException("Klarte ikke hente ut verdier!")
    }

    private fun getValue(key: String) : String? {
        connection.use {
            return connection.sync().get(key)
        }
    }

    fun shutdown() {
        connection.close()
        redisClient.shutdown()
    }

}
