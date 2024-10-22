package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.db.exposed.Database
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db.SelvbestemtRepo
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.FerdigstillForespoerselSakOgOppgaveRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.FjernPaaminnelseRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.ForespoerselLagretRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.OpprettOppgaveLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.OpprettSakLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.OpprettSelvbestemtSakRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.SlettSakLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.UtgaattLoeser
import no.nav.helsearbeidsgiver.tokenprovider.oauth2ClientCredentialsTokenGetter
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-notifikasjon".logger()

fun main() {
    val database = Database("NAIS_DATABASE_IM_NOTIFIKASJON_NOTIFIKASJON")

    logger.info("Migrering starter...")
    database.migrate()
    logger.info("Migrering ferdig.")

    val selvbestemtRepo = SelvbestemtRepo(database.db)

    RapidApplication
        .create(System.getenv())
        .createNotifikasjonService()
        .createNotifikasjonRivers(
            Env.linkUrl,
            selvbestemtRepo,
            buildClient(),
        ).registerShutdownLifecycle {
            logger.info("Stoppsignal mottatt, lukker databasetilkobling.")
            database.dataSource.close()
        }.start()
}

fun RapidsConnection.createNotifikasjonService(): RapidsConnection =
    also {
        logger.info("Starter ${HentDataTilSakOgOppgaveService::class.simpleName}...")
        ServiceRiverStateless(
            HentDataTilSakOgOppgaveService(this),
        ).connect(this)
    }

fun RapidsConnection.createNotifikasjonRivers(
    linkUrl: String,
    selvbestemtRepo: SelvbestemtRepo,
    arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
): RapidsConnection =
    also {
        logger.info("Starter ${FerdigstillForespoerselSakOgOppgaveRiver::class.simpleName}...")
        FerdigstillForespoerselSakOgOppgaveRiver(linkUrl, arbeidsgiverNotifikasjonKlient).connect(this)

        logger.info("Starter ${ForespoerselLagretRiver::class.simpleName}...")
        ForespoerselLagretRiver(this)

        logger.info("Starter ${OpprettSakLoeser::class.simpleName}...")
        OpprettSakLoeser(this, arbeidsgiverNotifikasjonKlient, linkUrl)

        logger.info("Starter ${OpprettOppgaveLoeser::class.simpleName}...")
        OpprettOppgaveLoeser(this, arbeidsgiverNotifikasjonKlient, linkUrl)

        logger.info("Starter ${UtgaattLoeser::class.simpleName}...")
        UtgaattLoeser(this, arbeidsgiverNotifikasjonKlient, linkUrl)

        logger.info("Starter ${SlettSakLoeser::class.simpleName}...")
        SlettSakLoeser(this, arbeidsgiverNotifikasjonKlient)

        logger.info("Starter ${OpprettSelvbestemtSakRiver::class.simpleName}...")
        OpprettSelvbestemtSakRiver(linkUrl, selvbestemtRepo, arbeidsgiverNotifikasjonKlient).connect(this)

        logger.info("Starter ${FjernPaaminnelseRiver::class.simpleName}...")
        FjernPaaminnelseRiver(arbeidsgiverNotifikasjonKlient).connect(this)
    }

private fun buildClient(): ArbeidsgiverNotifikasjonKlient {
    val tokenGetter = oauth2ClientCredentialsTokenGetter(Env.oauth2Environment)
    return ArbeidsgiverNotifikasjonKlient(Env.notifikasjonUrl, tokenGetter)
}
