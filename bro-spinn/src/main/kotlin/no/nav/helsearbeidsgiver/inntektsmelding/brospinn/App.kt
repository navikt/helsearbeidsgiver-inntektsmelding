package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.oauth2.OAuth2ClientConfig
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-bro-spinn".logger()

fun main() {
    logger.info("Jeg er oppe og kjører!")

    val redisStore = RedisStore(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createEksternInntektsmeldingLoeser(createSpinnKlient())
        .createSpinnService(redisStore)
        .registerShutdownLifecycle {
            redisStore.shutdown()
        }
        .start()

    logger.info("Bye bye, baby, bye bye!")
}

fun RapidsConnection.createEksternInntektsmeldingLoeser(spinnKlient: SpinnKlient): RapidsConnection =
    also {
        logger.info("Starter ${EksternInntektsmeldingLoeser::class.simpleName}...")
        EksternInntektsmeldingLoeser(this, spinnKlient)
    }

fun RapidsConnection.createSpinnService(redisStore: RedisStore): RapidsConnection =
    also {
        logger.info("Starter ${SpinnService::class.simpleName}...")
        SpinnService(this, redisStore)
    }

fun createSpinnKlient(): SpinnKlient {
    val tokenProvider = OAuth2ClientConfig(Env.azureOAuthEnvironment)
    return SpinnKlient(Env.spinnUrl, tokenProvider::getToken)
}
