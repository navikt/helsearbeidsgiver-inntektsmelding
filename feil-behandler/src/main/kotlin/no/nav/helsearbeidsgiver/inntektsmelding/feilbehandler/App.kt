package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbService
import no.nav.hag.utils.bakgrunnsjobb.PostgresBakgrunnsjobbRepository
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.config.Database
import no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.prosessor.FeilProsessor
import no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.river.FeilLytter
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-feil-behandler".logger()

fun main() {
    buildApp(System.getenv()).start()
}

fun buildApp(env: Map<String, String>): RapidsConnection {
    val database = Database("NAIS_DATABASE_IM_FEIL_BEHANDLER_IM_ERROR_RECOVERY")

    logger.info("Migrering starter...")
    database.migrate()
    logger.info("Migrering ferdig.")

    return RapidApplication
        .create(env)
        .createFeilLytter(database)
        .registerShutdownLifecycle {
            logger.info("Stoppsignal mottatt, lukker databasetilkobling.")
            database.dataSource.close()
        }
}

fun RapidsConnection.createFeilLytter(database: Database): RapidsConnection =
    also {
        createFeilLytter(repository = PostgresBakgrunnsjobbRepository(database.dataSource))
    }

fun RapidsConnection.createFeilLytter(repository: PostgresBakgrunnsjobbRepository): RapidsConnection =
    also {
        FeilLytter(it, repository)
        val bgService = BakgrunnsjobbService(repository)
        bgService.registrer(FeilProsessor(it))
        bgService.startAsync(true)
    }
