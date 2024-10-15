package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.db.exposed.Database
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateful
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db.SelvbestemtRepo
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.ForespoerselLagretRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.OppgaveFerdigLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.OpprettOppgaveLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.OpprettSakLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.OpprettSelvbestemtSakRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.SakFerdigLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.SlettSakLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.service.OpprettOppgaveService
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.service.OpprettSakService
import no.nav.helsearbeidsgiver.tokenprovider.oauth2ClientCredentialsTokenGetter
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-notifikasjon".logger()

fun main() {
    val redisConnection = RedisConnection(Env.redisUrl)

    val database = Database("NAIS_DATABASE_IM_NOTIFIKASJON_NOTIFIKASJON")

    logger.info("Migrering starter...")
    database.migrate()
    logger.info("Migrering ferdig.")

    val selvbestemtRepo = SelvbestemtRepo(database.db)

    RapidApplication
        .create(System.getenv())
        .createNotifikasjonServices(redisConnection)
        .createNotifikasjonRivers(
            Env.linkUrl,
            selvbestemtRepo,
            buildClient(),
        ).registerShutdownLifecycle {
            redisConnection.close()

            logger.info("Stoppsignal mottatt, lukker databasetilkobling.")
            database.dataSource.close()
        }.start()
}

fun RapidsConnection.createNotifikasjonServices(redisConnection: RedisConnection): RapidsConnection =
    also {
        logger.info("Starter ${OpprettSakService::class.simpleName}...")
        ServiceRiverStateful(
            OpprettSakService(
                rapid = this,
                redisStore = RedisStore(redisConnection, RedisPrefix.OpprettSak),
            ),
        ).connect(this)

        logger.info("Starter ${OpprettOppgaveService::class.simpleName}...")
        ServiceRiverStateless(
            OpprettOppgaveService(this),
        ).connect(this)
    }

fun RapidsConnection.createNotifikasjonRivers(
    linkUrl: String,
    selvbestemtRepo: SelvbestemtRepo,
    arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
): RapidsConnection =
    also {
        logger.info("Starter ${ForespoerselLagretRiver::class.simpleName}...")
        ForespoerselLagretRiver(this)

        logger.info("Starter ${OpprettSakLoeser::class.simpleName}...")
        OpprettSakLoeser(this, arbeidsgiverNotifikasjonKlient, linkUrl)

        logger.info("Starter ${SakFerdigLoeser::class.simpleName}...")
        SakFerdigLoeser(this, arbeidsgiverNotifikasjonKlient, linkUrl)

        logger.info("Starter ${OpprettOppgaveLoeser::class.simpleName}...")
        OpprettOppgaveLoeser(this, arbeidsgiverNotifikasjonKlient, linkUrl)

        logger.info("Starter ${OppgaveFerdigLoeser::class.simpleName}...")
        OppgaveFerdigLoeser(this, arbeidsgiverNotifikasjonKlient)

        logger.info("Starter ${SlettSakLoeser::class.simpleName}...")
        SlettSakLoeser(this, arbeidsgiverNotifikasjonKlient)

        logger.info("Starter ${OpprettSelvbestemtSakRiver::class.simpleName}...")
        OpprettSelvbestemtSakRiver(linkUrl, selvbestemtRepo, arbeidsgiverNotifikasjonKlient).connect(this)
    }

private fun buildClient(): ArbeidsgiverNotifikasjonKlient {
    val tokenGetter = oauth2ClientCredentialsTokenGetter(Env.oauth2Environment)
    return ArbeidsgiverNotifikasjonKlient(Env.notifikasjonUrl, tokenGetter)
}
