package no.nav.helsearbeidsgiver.inntektsmelding.api

import kotlinx.coroutines.delay
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

private const val MAX_RETRIES = 15

class RedisPoller(
    private val redisStore: RedisStore,
) {
    val logger = logger()
    val sikkerLogger = sikkerLogger()

    suspend fun hent(key: UUID): ResultJson? {
        repeat(MAX_RETRIES) {
            val result = redisStore.lesResultat(key)

            if (result != null) {
                return result
            } else {
                // 15 retries = 12000 ms total
                delay((it + 1) * 100L)
            }
        }

        "Redis brukte for lang tid på å svare (key='$key').".also {
            logger.error(it)
            sikkerLogger.error(it)
        }

        return null
    }
}
