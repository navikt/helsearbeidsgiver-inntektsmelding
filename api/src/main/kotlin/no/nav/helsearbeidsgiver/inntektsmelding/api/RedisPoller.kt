package no.nav.helsearbeidsgiver.inntektsmelding.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.lettuce.core.RedisClient
import kotlinx.coroutines.delay
import no.nav.helsearbeidsgiver.felles.Resultat

class RedisPoller(val redisClient: RedisClient, val objectMapper: ObjectMapper) {

    suspend fun getResultat(key: String, maxRetries: Int = 10, waitMillis: Long = 500): Resultat {
        val data = getValue(key, maxRetries, waitMillis)
        try {
            return objectMapper.readValue<Resultat>(data)
        } catch (ex: Exception) {
            throw RedisPollerJsonException(key, data)
        }
    }

    suspend fun getValue(key: String, maxRetries: Int = 10, waitMillis: Long = 500): String {
        val connection = redisClient.connect()
        connection.use {
            for (x in 0..maxRetries) {
                val value = it.sync().get(key)
                logger.info("Hent verdi for: $key = $value")
                if (value.isNullOrEmpty()) {
                    delay(waitMillis)
                } else {
                    return value
                }
            }
        }
        throw RedisPollerTimeoutException(key)
    }
}

open class RedisPollerException(message: String) : Exception(
    message
)

class RedisPollerTimeoutException(uuid: String) : RedisPollerException(
    "Brukte for lang tid på å svare ($uuid)"
)

class RedisPollerJsonException(uuid: String, data: String) : RedisPollerException(
    "Klarte ikke å parse ($uuid) med json: $data"
)
