package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.db.exposed.Database
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
import no.nav.helsearbeidsgiver.tokenprovider.oauth2ClientCredentialsTokenGetter
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-notifikasjon".logger()

fun main() {
    val redisStore = RedisStore(Env.redisUrl)

    val database = Database("NAIS_DATABASE_IM_NOTIFIKASJON_NOTIFIKASJON")

    logger.info("Migrering starter...")
    database.migrate()
    logger.info("Migrering ferdig.")

    val aapenRepo = AapenRepo(database.db)

    RapidApplication
        .create(System.getenv())
        .createNotifikasjonRivers(
            Env.linkUrl,
            aapenRepo,
            redisStore,
            buildClient()
        )
        .registerShutdownLifecycle {
            redisStore.shutdown()

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

private fun buildClient(): ArbeidsgiverNotifikasjonKlient {
    val tokenGetter = oauth2ClientCredentialsTokenGetter(Env.oauth2Environment)
    return ArbeidsgiverNotifikasjonKlient(Env.notifikasjonUrl, tokenGetter)
}
