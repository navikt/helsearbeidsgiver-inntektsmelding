package no.nav.helsearbeidsgiver.inntektsmelding.trengerservice

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.rapidsrivers.onShutdown
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateful
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-hent-forespoersel-service".logger()

fun main() {
    val redisConnection =
        RedisConnection(
            host = Env.redisHost,
            port = Env.redisPort,
            username = Env.redisUsername,
            password = Env.redisPassword,
        )

    RapidApplication
        .create(System.getenv())
        .createHentForespoerselService(redisConnection)
        .onShutdown {
            redisConnection.close()
        }.start()
}

fun RapidsConnection.createHentForespoerselService(redisConnection: RedisConnection): RapidsConnection =
    also {
        logger.info("Starter ${HentForespoerselService::class.simpleName}...")
        ServiceRiverStateful(
            HentForespoerselService(
                rapid = this,
                redisStore = RedisStore(redisConnection, RedisPrefix.HentForespoersel),
            ),
        ).connect(this)

        logger.info("Starter ${HentForespoerslerForVedtaksperiodeIdListeService::class.simpleName}...")
        ServiceRiverStateless(
            HentForespoerslerForVedtaksperiodeIdListeService(
                rapid = this,
                redisStore = RedisStore(redisConnection, RedisPrefix.HentForespoerslerForVedtaksperiodeIdListe),
            ),
        ).connect(this)
    }
