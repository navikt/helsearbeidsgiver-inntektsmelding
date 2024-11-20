package no.nav.helsearbeidsgiver.inntektsmelding.api

import kotlinx.coroutines.delay
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

private const val MAX_RETRIES = 10
private const val WAIT_MILLIS_DEFAULT = 500L
private val WAIT_MILLIS = List(MAX_RETRIES) { 100L * (1 + it) }

// TODO Bruke kotlin.Result istedenfor exceptions?
class RedisPoller(
    private val redisStore: RedisStore,
) {
    private val sikkerLogger = sikkerLogger()

    suspend fun hent(key: UUID): ResultJson {
        repeat(MAX_RETRIES) {
            sikkerLogger.debug("Polling redis: $it time(s) for key $key")

            val result = redisStore.lesResultat(key)

            if (result != null) {
                sikkerLogger.info("Hentet verdi for: '$key' = $result")
                return result
            } else {
                delay(WAIT_MILLIS.getOrNull(it) ?: WAIT_MILLIS_DEFAULT)
            }
        }

        throw RedisPollerTimeoutException(key)
    }
}

class RedisPollerTimeoutException(
    uuid: UUID,
) : Exception("Brukte for lang tid på å svare ($uuid).")
