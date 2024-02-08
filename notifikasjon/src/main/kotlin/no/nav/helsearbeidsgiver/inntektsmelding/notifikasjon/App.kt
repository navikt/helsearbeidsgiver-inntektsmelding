package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.oauth2.OAuth2ClientConfig
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

private val logger = "im-notifikasjon".logger()
val sikkerLogger = sikkerLogger()

fun main() {
    val redisStore = RedisStore(Env.redisUrl)

    RapidApplication
        .create(System.getenv())
        .createNotifikasjon(redisStore, buildClient(), Env.linkUrl)
        .registerShutdownLifecycle {
            redisStore.shutdown()
        }
        .start()
}

fun RapidsConnection.createNotifikasjon(
    redisStore: RedisStore,
    arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    linkUrl: String
): RapidsConnection =
    also {
        logger.info("Starter ${ForespoerselLagretListener::class.simpleName}...")
        ForespoerselLagretListener(this)

        logger.info("Starter ${OpprettSakService::class.simpleName}...")
        OpprettSakService(this, redisStore)

        logger.info("Starter ${OpprettSakLoeser::class.simpleName}...")
        OpprettSakLoeser(this, arbeidsgiverNotifikasjonKlient, linkUrl)

        logger.info("Starter ${SakFerdigLoeser::class.simpleName}...")
        SakFerdigLoeser(this, arbeidsgiverNotifikasjonKlient)

        logger.info("Starter ${OpprettOppgaveLoeser::class.simpleName}...")
        OpprettOppgaveLoeser(this, arbeidsgiverNotifikasjonKlient, linkUrl)

        logger.info("Starter ${OpprettOppgaveService::class.simpleName}...")
        OpprettOppgaveService(this, redisStore)

        logger.info("Starter ${OppgaveFerdigLoeser::class.simpleName}...")
        OppgaveFerdigLoeser(this, arbeidsgiverNotifikasjonKlient)

        logger.info("Starter ${ManuellOpprettSakService::class.simpleName}...")
        ManuellOpprettSakService(this, redisStore)

        logger.info("Starter ${SlettSakLoeser::class.simpleName}...")
        SlettSakLoeser(this, arbeidsgiverNotifikasjonKlient)
    }

private fun buildClient(): ArbeidsgiverNotifikasjonKlient {
    val tokenProvider = OAuth2ClientConfig(Env.azureOAuthEnvironment)
    return ArbeidsgiverNotifikasjonKlient(Env.notifikasjonUrl, tokenProvider::getToken)
}
