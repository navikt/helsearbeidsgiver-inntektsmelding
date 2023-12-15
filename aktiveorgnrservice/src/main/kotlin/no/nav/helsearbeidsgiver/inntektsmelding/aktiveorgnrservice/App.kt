package no.nav.helsearbeidsgiver.inntektsmelding.aktiveorgnrservice

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-aktiveorgnrservice".logger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createAktiveOrgnrService(RedisStore(Env.redisUrl))
        .start()
}

fun RapidsConnection.createAktiveOrgnrService(redisStore: RedisStore): RapidsConnection =
    also {
        logger.info("Starter ${AktiveOrgnrService::class.simpleName}...")
        AktiveOrgnrService(this, redisStore)
    }
