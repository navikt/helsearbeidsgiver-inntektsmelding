package no.nav.helsearbeidsgiver.inntektsmelding.api

import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

private const val MAX_RETRIES = 10
private const val WAIT_MILLIS = 500L

// TODO Bruke kotlin.Result istedenfor exceptions?
class RedisPoller {
    private val sikkerLogger = sikkerLogger()

    private val redis = RedisConnection(Env.Redis.url)

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

            val result = redis.get(key.toString())

            if (result != null) {
                return result
            } else {
                delay(WAIT_MILLIS)
            }
        }

        throw RedisPollerTimeoutException(key)
    }

    fun close() {
        redis.close()
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
