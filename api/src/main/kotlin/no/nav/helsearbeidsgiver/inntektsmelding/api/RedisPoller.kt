package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

// TODO Bruke kotlin.Result istedenfor exceptions?
class RedisPoller {
    private val redisClient = RedisClient.create(
        Env.Redis.url
    )
    private lateinit var connection: StatefulRedisConnection<String, String>
    private lateinit var syncCommands: RedisCommands<String, String>
    private val sikkerLogger = sikkerLogger()

    private fun redisCommand(): RedisCommands<String, String> {
        if (!::connection.isInitialized) {
            connection = redisClient.connect()
            syncCommands = connection.sync()
        }
        return syncCommands
    }

    suspend fun hent(key: UUID, maxRetries: Int = 10, waitMillis: Long = 500): JsonElement {
        val json = getString(key, maxRetries, waitMillis)

        sikkerLogger.info("Hentet verdi for: '$key' = $json")

        return try {
            json.parseJson()
        } catch (e: Exception) {
            "JSON-parsing feilet.".let {
                sikkerLogger.error("$it key=$key json=$json", e)
                throw RedisPollerJsonParseException("$it key='$key'", e)
            }
        }
    }

    suspend fun getString(key: UUID, maxRetries: Int, waitMillis: Long): String {
        repeat(maxRetries) {
            sikkerLogger.debug("Polling redis: $it time(s) for key $key")
            if (redisCommand().exists(key.toString()) == 1.toLong()) {
                return syncCommands.get(key.toString())
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

class RedisPollerTimeoutException(uuid: UUID) : RedisPollerException(
    "Brukte for lang tid på å svare ($uuid)."
)
