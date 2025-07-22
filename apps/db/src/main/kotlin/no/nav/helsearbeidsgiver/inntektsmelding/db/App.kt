package no.nav.helsearbeidsgiver.inntektsmelding.db

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.db.exposed.Database
import no.nav.helsearbeidsgiver.felles.rapidsrivers.onShutdown
import no.nav.helsearbeidsgiver.felles.rapidsrivers.onStartup
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

    return RapidApplication
        .create(System.getenv())
        .onStartup {
            logger.info("Migrering starter...")
            database.migrate()
            logger.info("Migrering ferdig.")
        }.onShutdown {
            logger.info("Stoppsignal mottatt, lukker databasetilkobling...")
            database.dataSource.close()
            logger.info("Databasetilkobling lukket.")
        }.createDbRivers(imRepo, selvbestemtImRepo)
        .start()
}

fun RapidsConnection.createDbRivers(
    imRepo: InntektsmeldingRepository,
    selvbestemtImRepo: SelvbestemtImRepo,
): RapidsConnection =
    also {
        logger.info("Starter ${HentLagretImRiver::class.simpleName}...")
        HentLagretImRiver(imRepo).connect(this)

        logger.info("Starter ${HentSelvbestemtImRiver::class.simpleName}...")
        HentSelvbestemtImRiver(selvbestemtImRepo).connect(this)

        logger.info("Starter ${LagreImSkjemaRiver::class.simpleName}...")
        LagreImSkjemaRiver(imRepo).connect(this)

        logger.info("Starter ${LagreImRiver::class.simpleName}...")
        LagreImRiver(imRepo).connect(this)

        logger.info("Starter ${LagreJournalpostIdRiver::class.simpleName}...")
        LagreJournalpostIdRiver(imRepo, selvbestemtImRepo).connect(this)

        logger.info("Starter ${LagreEksternImRiver::class.simpleName}...")
        LagreEksternImRiver(imRepo).connect(this)

        logger.info("Starter ${LagreSelvbestemtImRiver::class.simpleName}...")
        LagreSelvbestemtImRiver(selvbestemtImRepo).connect(this)

        logger.info("Starter ${OppdaterImSomProsessertRiver::class.simpleName}...")
        OppdaterImSomProsessertRiver(imRepo, selvbestemtImRepo).connect(this)
    }
