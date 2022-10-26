package no.nav.helsearbeidsgiver.inntektsmelding.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.lettuce.core.RedisClient
import kotlinx.coroutines.delay
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.fromEnv
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.loeser.Løsning

// TODO Bruke kotlin.Result istedenfor exceptions?
class RedisPoller {
    private val redisClient = RedisClient.create(
        "redis://${"REDIS_URL".fromEnv()}:6379/0"
    )
    private val objectMapper = customObjectMapper()

    suspend fun <T : Any> hent(id: String, maxRetries: Int = 10, waitMillis: Long = 500): Løsning<T> {
        val data = getValue(id, maxRetries, waitMillis)
        return try {
            Løsning.read(objectMapper, data)
        } catch (e: Exception) {
            "JSON-parsing av data feilet.".let {
                sikkerlogg.error("$it id=$id data=$data")
                throw RedisPollerJsonParseException("$it Se sikker logg for mer info. id=$id", e)
            }
        }
    }

    suspend fun getResultat(key: String, maxRetries: Int = 10, waitMillis: Long = 500): Resultat {
        val data = getValue(key, maxRetries, waitMillis)
        return try {
            objectMapper.readValue(data)
        } catch (ex: Exception) {
            throw RedisPollerJsonException(key, data)
        }
    }

    private suspend fun getValue(key: String, maxRetries: Int, waitMillis: Long): String {
        redisClient.connect().use { connection ->
            repeat(maxRetries) {
                val value = connection.sync().get(key)
                logger.info("Hentet verdi for: $key = $value")

                if (!value.isNullOrEmpty()) return value

                delay(waitMillis)
            }
        }

        throw RedisPollerTimeoutException(key)
    }
}

sealed class RedisPollerException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class RedisPollerJsonParseException(message: String, cause: Throwable) : RedisPollerException(message, cause)

class RedisPollerJsonException(uuid: String, data: String) : RedisPollerException(
    "Klarte ikke å parse ($uuid) med json: $data"
)

class RedisPollerTimeoutException(uuid: String) : RedisPollerException(
    "Brukte for lang tid på å svare ($uuid)."
)
