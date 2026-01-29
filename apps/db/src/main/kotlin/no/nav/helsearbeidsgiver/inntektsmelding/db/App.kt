package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.hag.simba.utils.db.exposed.Database
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.HentLagretImRiver
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.HentSelvbestemtImRiver
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreEksternImRiver
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreImRiver
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreImSkjemaRiver
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreJournalpostIdRiver
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreSelvbestemtImRiver
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.OppdaterImSomProsessertRiver
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-db".logger()

fun main() {
    val database = Database("NAIS_DATABASE_IM_DB_INNTEKTSMELDING")

    val imRepo = InntektsmeldingRepository(database.db)
    val selvbestemtImRepo = SelvbestemtImRepo(database.db)

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
        createDbRivers(imRepo, selvbestemtImRepo)
    }
}

fun createDbRivers(
    imRepo: InntektsmeldingRepository,
    selvbestemtImRepo: SelvbestemtImRepo,
): List<ObjectRiver.Simba<*>> =
    listOf(
        HentLagretImRiver(imRepo),
        HentSelvbestemtImRiver(selvbestemtImRepo),
        LagreImSkjemaRiver(imRepo),
        LagreImRiver(imRepo),
        LagreJournalpostIdRiver(imRepo, selvbestemtImRepo),
        LagreEksternImRiver(imRepo),
        LagreSelvbestemtImRiver(selvbestemtImRepo),
        OppdaterImSomProsessertRiver(imRepo, selvbestemtImRepo),
    )
