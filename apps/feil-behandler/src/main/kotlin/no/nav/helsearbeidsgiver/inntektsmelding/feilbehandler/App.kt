package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler

import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbService
import no.nav.hag.utils.bakgrunnsjobb.PostgresBakgrunnsjobbRepository
import no.nav.helsearbeidsgiver.felles.rr.Publisher
import no.nav.helsearbeidsgiver.felles.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.config.Database
import no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.prosessor.FeilProsessor
import no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.river.FeilLytter
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-feil-behandler".logger()

fun main() {
    val database = Database("NAIS_DATABASE_IM_FEIL_BEHANDLER_IM_ERROR_RECOVERY")

    ObjectRiver.connectToRapid(
        onStartup = {
            logger.info("Migrering starter...")
            database.migrate()
            logger.info("Migrering ferdig.")
        },
        onShutdown = {
            logger.info("Stoppsignal mottatt, lukker databasetilkobling...")
            database.dataSource.close()
            logger.info("Databasetilkobling lukket.")
        },
    ) {
        createFeilLytter(
            publisher = it,
            repository = PostgresBakgrunnsjobbRepository(database.dataSource),
        )
    }
}

fun createFeilLytter(
    publisher: Publisher,
    repository: PostgresBakgrunnsjobbRepository,
): List<ObjectRiver.Simba<*>> {
    val bgService = BakgrunnsjobbService(repository)
    bgService.registrer(FeilProsessor(publisher))
    bgService.startAsync(true)

    return listOf(FeilLytter(repository))
}
