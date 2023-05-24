package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

// TODO Bruke kotlin.Result istedenfor exceptions?
class RedisPoller {
    private val redisClient = RedisClient.create(
        Env.Redis.url
    )
    private lateinit var connection: StatefulRedisConnection<String, String>
    private lateinit var syncCommands: RedisCommands<String, String>
    private val sikkerLogger = sikkerLogger()

    private fun redisCommand(): RedisCommands<String, String> {
        if (connection == null) {
            connection = redisClient.connect()
            syncCommands = connection.sync()
        }
        return syncCommands
    }

    suspend fun hent(key: String, maxRetries: Int = 10, waitMillis: Long = 500): JsonElement {
        val json = getString(key, maxRetries, waitMillis)

        sikkerLogger.info("Hentet verdi for: $key = $json")

        return try {
            json.parseJson()
        } catch (e: Exception) {
            "JSON-parsing feilet.".let {
                sikkerLogger.error("$it key=$key json=$json")
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
        repeat(maxRetries) {
            sikkerLogger.debug("Polling redis: $it time(s) for key $key")
            if (redisCommand().exists(key) == 1.toLong()) {
                return syncCommands.get(key)
            }
            delay(waitMillis)
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
