package no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-tilgangservice".logger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createTilgangService(RedisStore(Env.redisUrl))
        .start()
}

fun RapidsConnection.createTilgangService(redisStore: RedisStore): RapidsConnection =
    also {
        logger.info("Starter ${TilgangService::class.simpleName}...")
        TilgangService(this, redisStore)
    }
