package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.lettuce.core.RedisClient
import kotlinx.coroutines.delay
import java.util.concurrent.TimeoutException

class RedisPoller(val redisClient: RedisClient) {

    suspend fun getValue(key: String, maxRetries: Int = 10, waitMillis: Long = 500): String {
        logger.info("Poller starter...")
        val connection = redisClient.connect()
        logger.info("Fikk connection.")
        connection.use {
            for (x in 0..maxRetries) {
                logger.info("Hent verdi")
                val value = it.sync().get(key)
                logger.info("Fikk verdi: $value")
                if (value.isNullOrEmpty()) {
                    delay(waitMillis)
                } else {
                    return value
                }
            }
        }
        throw TimeoutException("Klarte ikke hente ut verdier!")
    }

    fun shutdown() {
        redisClient.shutdown()
    }
}
