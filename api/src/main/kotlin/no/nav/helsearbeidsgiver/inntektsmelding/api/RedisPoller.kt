package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.lettuce.core.RedisClient
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

private const val MAX_RETRIES = 10
private const val WAIT_MILLIS_DEFAULT = 500L
private val WAIT_MILLIS = List(MAX_RETRIES) { 100L * (1 + it) }

// TODO Bruke kotlin.Result istedenfor exceptions?
class RedisPoller {
    private val sikkerLogger = sikkerLogger()

    private val redisClient = Env.Redis.url.let(RedisClient::create)
    private val connection = redisClient.connect()
    private val syncCommands = connection.sync()

    suspend fun hent(key: UUID): JsonElement {
        val json = hentJsonString(key)

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

    private suspend fun hentJsonString(key: UUID): String {
        repeat(MAX_RETRIES) {
            sikkerLogger.debug("Polling redis: $it time(s) for key $key")

            val result = syncCommands.get(key.toString())

            if (result != null) {
                return result
            } else {
                delay(WAIT_MILLIS.getOrNull(it) ?: WAIT_MILLIS_DEFAULT)
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

class RedisPollerTimeoutException(uuid: UUID) : RedisPollerException(
    "Brukte for lang tid på å svare ($uuid)."
)
