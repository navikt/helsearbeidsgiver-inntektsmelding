package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.lettuce.core.RedisClient
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

// TODO Bruke kotlin.Result istedenfor exceptions?
// TODO rydd opp i sikkerlogg vs loggerSikker
class RedisPoller {
    private val redisClient = RedisClient.create(
        Env.Redis.url
    )
    private val sikkerLogger = sikkerLogger()

    suspend fun hent(key: String, maxRetries: Int = 10, waitMillis: Long = 500): JsonElement {
        val json = getString(key, maxRetries, waitMillis)

        sikkerLogger.info("Hentet verdi for: $key = $json")

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

    suspend fun getString(key: String, maxRetries: Int, waitMillis: Long): String {
        redisClient.connect().use { connection ->
            repeat(maxRetries) {
                val str = connection.sync().get(key)

                if (!str.isNullOrEmpty()) return str

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
