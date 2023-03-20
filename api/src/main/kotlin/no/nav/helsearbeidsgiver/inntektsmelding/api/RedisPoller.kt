package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.lettuce.core.RedisClient
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.parseJson
import no.nav.helsearbeidsgiver.felles.log.loggerSikker

// TODO Bruke kotlin.Result istedenfor exceptions?
class RedisPoller {
    private val redisClient = RedisClient.create(
        "redis://${Env.Redis.url}:6379/0"
    )
    private val loggerSikker = loggerSikker()

    suspend fun hent(key: String, maxRetries: Int = 10, waitMillis: Long = 500): JsonElement {
        val json = getJson(key, maxRetries, waitMillis)

        loggerSikker.info("Hentet verdi for: $key = $json")

        return try {
            json.parseJson()
        } catch (e: Exception) {
            "JSON-parsing feilet.".let {
                sikkerlogg.error("$it key=$key json=$json")
                throw RedisPollerJsonParseException("$it Se sikker logg for mer info. key=$key", e)
            }
        }
    }

    suspend fun getResultat(key: String, maxRetries: Int = 10, waitMillis: Long = 500): Resultat {
        val json = hent(key, maxRetries, waitMillis)
        return try {
            json.fromJson(Resultat.serializer())
        } catch (_: Exception) {
            throw RedisPollerJsonException(key, json.toString())
        }
    }

    private suspend fun getJson(key: String, maxRetries: Int, waitMillis: Long): String {
        redisClient.connect().use { connection ->
            repeat(maxRetries) {
                val json = connection.sync().get(key)

                if (!json.isNullOrEmpty()) return json

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

class RedisPollerJsonException(uuid: String, json: String) : RedisPollerException(
    "Klarte ikke å parse ($uuid) med json: $json"
)

class RedisPollerTimeoutException(uuid: String) : RedisPollerException(
    "Brukte for lang tid på å svare ($uuid)."
)
