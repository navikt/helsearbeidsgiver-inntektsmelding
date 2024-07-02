package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiver
import no.nav.helsearbeidsgiver.tokenprovider.oauth2ClientCredentialsTokenGetter
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-bro-spinn".logger()

fun main() {
    logger.info("Jeg er oppe og kjører!")

    val redisConnection = RedisConnection(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createEksternInntektsmeldingLoeser(createSpinnKlient())
        .createSpinnService(redisConnection)
        .registerShutdownLifecycle {
            redisConnection.close()
        }
        .start()

    logger.info("Bye bye, baby, bye bye!")
}

fun RapidsConnection.createEksternInntektsmeldingLoeser(spinnKlient: SpinnKlient): RapidsConnection =
    also {
        logger.info("Starter ${EksternInntektsmeldingLoeser::class.simpleName}...")
        EksternInntektsmeldingLoeser(this, spinnKlient)
    }

fun RapidsConnection.createSpinnService(redisConnection: RedisConnection): RapidsConnection =
    also {
        logger.info("Starter ${SpinnService::class.simpleName}...")
        ServiceRiver(
            SpinnService(
                rapid = this,
                redisStore = RedisStoreClassSpecific(redisConnection, RedisPrefix.SpinnService)
            )
        ).connect(this)
    }

fun createSpinnKlient(): SpinnKlient {
    val tokenGetter = oauth2ClientCredentialsTokenGetter(Env.oauth2Environment)
    return SpinnKlient(Env.spinnUrl, tokenGetter)
}
