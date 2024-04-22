package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.db.exposed.Database
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db.ForespoerselOppgaveRepo
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db.ForespoerselSakRepo
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db.SelvbestemtSakRepo
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.FerdigstillForespoerselOppgaveRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.FerdigstillForespoerselSakRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.OpprettForespoerselOppgaveRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.OpprettForespoerselSakRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.OpprettSelvbestemtSakRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.SlettSakLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.service.ManuellOpprettSakService
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.service.OpprettForespoerselNotifikasjonService
import no.nav.helsearbeidsgiver.tokenprovider.oauth2ClientCredentialsTokenGetter
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-notifikasjon".logger()

fun main() {
    val redisStore = RedisStore(Env.redisUrl)

    val database = Database("NAIS_DATABASE_IM_NOTIFIKASJON_NOTIFIKASJON")

    logger.info("Migrering starter...")
    database.migrate()
    logger.info("Migrering ferdig.")

    val forespoerselSakRepo = ForespoerselSakRepo(database.db)
    val forespoerselOppgaveRepo = ForespoerselOppgaveRepo(database.db)
    val selvbestemtSakRepo = SelvbestemtSakRepo(database.db)

    RapidApplication
        .create(System.getenv())
        .createNotifikasjonRivers(
            Env.linkUrl,
            forespoerselSakRepo,
            forespoerselOppgaveRepo,
            selvbestemtSakRepo,
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
    forespoerselSakRepo: ForespoerselSakRepo,
    forespoerselOppgaveRepo: ForespoerselOppgaveRepo,
    selvbestemtSakRepo: SelvbestemtSakRepo,
    redisStore: RedisStore,
    arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient
): RapidsConnection =
    also {
        logger.info("Starter ${OpprettForespoerselNotifikasjonService::class.simpleName}...")
        OpprettForespoerselNotifikasjonService(this)

        logger.info("Starter ${OpprettSakService::class.simpleName}...")
        OpprettSakService(this, redisStore)

        logger.info("Starter ${OpprettOppgaveService::class.simpleName}...")
        OpprettOppgaveService(this, redisStore)

        logger.info("Starter ${OpprettForespoerselSakRiver::class.simpleName}...")
        OpprettForespoerselSakRiver(linkUrl, arbeidsgiverNotifikasjonKlient, forespoerselSakRepo).connect(this)

        logger.info("Starter ${OpprettForespoerselOppgaveRiver::class.simpleName}...")
        OpprettForespoerselOppgaveRiver(linkUrl, arbeidsgiverNotifikasjonKlient, forespoerselOppgaveRepo).connect(this)

        logger.info("Starter ${OpprettSelvbestemtSakRiver::class.simpleName}...")
        OpprettSelvbestemtSakRiver(linkUrl, arbeidsgiverNotifikasjonKlient, selvbestemtSakRepo).connect(this)

        logger.info("Starter ${FerdigstillForespoerselSakRiver::class.simpleName}...")
        FerdigstillForespoerselSakRiver(arbeidsgiverNotifikasjonKlient, forespoerselSakRepo).connect(this)

        logger.info("Starter ${FerdigstillForespoerselOppgaveRiver::class.simpleName}...")
        FerdigstillForespoerselOppgaveRiver(arbeidsgiverNotifikasjonKlient, forespoerselOppgaveRepo).connect(this)

        // Trigges manuelt
        logger.info("Starter ${ManuellOpprettSakService::class.simpleName}...")
        ManuellOpprettSakService(this, redisStore)

        logger.info("Starter ${SlettSakLoeser::class.simpleName}...")
        SlettSakLoeser(this, arbeidsgiverNotifikasjonKlient)
    }

private fun buildClient(): ArbeidsgiverNotifikasjonKlient {
    val tokenGetter = oauth2ClientCredentialsTokenGetter(Env.oauth2Environment)
    return ArbeidsgiverNotifikasjonKlient(Env.notifikasjonUrl, tokenGetter)
}
