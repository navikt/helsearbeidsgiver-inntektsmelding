package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.db.Database
import no.nav.helsearbeidsgiver.felles.oauth2.OAuth2ClientConfig
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db.AapenRepo
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.ForespoerselLagretListener
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.OppgaveFerdigLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.OpprettAapenSakRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.OpprettOppgaveLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.OpprettSakLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.SakFerdigLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.SlettSakLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.service.ManuellOpprettSakService
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.service.OpprettOppgaveService
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.service.OpprettSakService
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-notifikasjon".logger()

fun main() {
    val env = setUpEnvironment()

    val database = Database(Database.Secrets("NAIS_DATABASE_IM_NOTIFIKASJON_SAK_OPPGAVE"))

    logger.info("Migrering starter...")
    database.migrate()
    logger.info("Migrering ferdig.")

    val aapenRepo = AapenRepo(database.db)

    RapidApplication
        .create(System.getenv())
        .createNotifikasjonRivers(
            env.linkUrl,
            aapenRepo,
            RedisStore(env.redisUrl),
            buildClient(env)
        )
        .registerShutdownLifecycle {
            logger.info("Stoppsignal mottatt, lukker databasetilkobling.")
            database.dataSource.close()
        }
        .start()
}

fun RapidsConnection.createNotifikasjonRivers(
    linkUrl: String,
    aapenRepo: AapenRepo,
    redisStore: RedisStore,
    arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient
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

        logger.info("Starter ${OpprettAapenSakRiver::class.simpleName}...")
        OpprettAapenSakRiver(linkUrl, aapenRepo, arbeidsgiverNotifikasjonKlient).connect(this)
    }

fun buildClient(environment: Environment): ArbeidsgiverNotifikasjonKlient {
    val tokenProvider = OAuth2ClientConfig(environment.azureOAuthEnvironment)
    return ArbeidsgiverNotifikasjonKlient(environment.notifikasjonUrl, tokenProvider::getToken)
}
