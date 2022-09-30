package no.nav.helsearbeidsgiver.inntektsmelding.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.lettuce.core.RedisClient
import kotlinx.coroutines.delay
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.felles.Resultat

class RedisPoller(val redisClient: RedisClient, val objectMapper: ObjectMapper) {

    suspend fun getResultat(key: String, maxRetries: Int = 10, waitMillis: Long = 500): Resultat {
        val data = getValue(key, maxRetries, waitMillis)
        return transformResultat(data, key)
    }

    fun transformResultat(data: String, key: String): Resultat {
        try {
            val map = objectMapper.readValue<Map<String, Løsning>>(data)
            return Resultat(map.values.toList())
        } catch (ex: Exception) {
            ex.printStackTrace()
            throw RedisPollerJsonException(key)
        }
    }

    suspend fun getValue(key: String, maxRetries: Int = 10, waitMillis: Long = 500): String {
        logger.info("Poller starter...")
        val connection = redisClient.connect()
        logger.info("Fikk connection.")
        connection.use {
            for (x in 0..maxRetries) {
                logger.info("Hent verdi for: $key")
                val value = it.sync().get(key)
                logger.info("Fikk verdi: $value")
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

class RedisPollerJsonException(uuid: String) : RedisPollerException(
    "Klarte ikke å parse Json data ($uuid)"
)
