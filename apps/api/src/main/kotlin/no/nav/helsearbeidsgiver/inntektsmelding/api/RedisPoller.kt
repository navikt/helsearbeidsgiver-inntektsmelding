package no.nav.helsearbeidsgiver.inntektsmelding.api

import kotlinx.coroutines.delay
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.valkey.RedisStore
import java.util.UUID

private const val MAX_RETRIES = 15
private const val WAIT_MILLIS_DEFAULT = 500L
private val WAIT_MILLIS = List(MAX_RETRIES) { 100L * (1 + it) } // 15 retries = 12000 ms total

// TODO Bruke kotlin.Result istedenfor exceptions?
class RedisPoller(
    private val redisStore: RedisStore,
) {
    suspend fun hent(key: UUID): ResultJson {
        repeat(MAX_RETRIES) {
            val result = redisStore.lesResultat(key)

            if (result != null) {
                return result
            } else {
                delay(WAIT_MILLIS.getOrNull(it) ?: WAIT_MILLIS_DEFAULT)
            }
        }

        throw RedisPollerTimeoutException(key)
    }
}

class RedisPollerTimeoutException(
    key: UUID,
) : Exception("Brukte for lang tid på å svare ($key).")
