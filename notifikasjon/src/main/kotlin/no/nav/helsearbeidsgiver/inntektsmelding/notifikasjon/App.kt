package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.oauth2.OAuth2ClientConfig
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

private val logger = "im-notifikasjon".logger()
val sikkerLogger = sikkerLogger()

fun main() {
    val environment = setUpEnvironment()
    RapidApplication
        .create(System.getenv())
        .createNotifikasjon(buildRedisStore(environment), buildClient(environment), environment.linkUrl)
        .start()
}

fun RapidsConnection.createNotifikasjon(
    redisStore: RedisStore,
    arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    linkUrl: String
): RapidsConnection =
    also {
        logger.info("Starter ${OpprettSak::class.simpleName}...")
        OpprettSak(this, redisStore)

        logger.info("Starter ${OpprettSakLoeser::class.simpleName}...")
        OpprettSakLoeser(this, arbeidsgiverNotifikasjonKlient, linkUrl)

        logger.info("Starter ${SakFerdigLoeser::class.simpleName}...")
        SakFerdigLoeser(this, arbeidsgiverNotifikasjonKlient)

        logger.info("Starter ${OpprettOppgaveLoeser::class.simpleName}...")
        OpprettOppgaveLoeser(this, arbeidsgiverNotifikasjonKlient, linkUrl)

        logger.info("Starter ${OpprettOppgave::class.simpleName}...")
        OpprettOppgave(this, redisStore)

        logger.info("Starter ${OppgaveFerdigLoeser::class.simpleName}...")
        OppgaveFerdigLoeser(this, arbeidsgiverNotifikasjonKlient)

        logger.info("Starter ${ManuellOpprettSakService::class.simpleName}...")
        ManuellOpprettSakService(this, redisStore)
    }

fun buildClient(environment: Environment): ArbeidsgiverNotifikasjonKlient {
    val tokenProvider = OAuth2ClientConfig(environment.azureOAuthEnvironment)
    return ArbeidsgiverNotifikasjonKlient(environment.notifikasjonUrl, tokenProvider::getToken)
}

fun buildRedisStore(environment: Environment): RedisStore {
    sikkerLogger.info("Redis url er " + environment.redisUrl)
    return RedisStore(environment.redisUrl)
}
