package no.nav.helsearbeidsgiver.inntektsmelding.api

import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

private const val MAX_RETRIES = 10
private const val WAIT_MILLIS = 500L

// TODO Bruke kotlin.Result istedenfor exceptions?
class RedisPoller(
    private val redisStore: RedisStoreClassSpecific,
) {
    private val sikkerLogger = sikkerLogger()

    suspend fun hent(key: UUID): JsonElement {
        repeat(MAX_RETRIES) {
            sikkerLogger.debug("Polling redis: $it time(s) for key $key")

            val result = redisStore.get(RedisKey.of(key))

            if (result != null) {
                sikkerLogger.info("Hentet verdi for: '$key' = $result")
                return result
            } else {
                delay(WAIT_MILLIS)
            }
        }

        throw RedisPollerTimeoutException(key)
    }
}

class RedisPollerTimeoutException(
    uuid: UUID,
) : Exception("Brukte for lang tid på å svare ($uuid).")
