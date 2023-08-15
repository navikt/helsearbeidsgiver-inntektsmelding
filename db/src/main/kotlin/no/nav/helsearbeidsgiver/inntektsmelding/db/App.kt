package no.nav.helsearbeidsgiver.inntektsmelding.db

import com.zaxxer.hikari.HikariConfig
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.Database
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.DatabaseConfig
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.mapHikariConfig
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.HentOrgnrLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.HentPersistertLøser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreForespoerselLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreJournalpostIdLøser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.NotifikasjonHentIdLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.PersisterImLøser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.PersisterOppgaveLøser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.PersisterSakLøser
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

private val logger = "helsearbeidsgiver-im-db".logger()
private val sikkerLogger = sikkerLogger()

fun main() {
    buildApp(mapHikariConfig(DatabaseConfig()), System.getenv()).start()
}

fun buildApp(config: HikariConfig, env: Map<String, String>): RapidsConnection {
    val database = Database(config)
    sikkerLogger.info("Bruker database url: ${config.jdbcUrl}")
    logger.info("Migrering starter...")
    database.migrate()
    logger.info("Migrering ferdig.")
    val imRepo = InntektsmeldingRepository(database.db)
    val forespoerselRepo = ForespoerselRepository(database.db)
    return RapidApplication
        .create(env)
        .createDb(database, imRepo, forespoerselRepo)
}

fun RapidsConnection.createDb(database: Database, imRepo: InntektsmeldingRepository, forespoerselRepo: ForespoerselRepository): RapidsConnection =
    apply {
        logger.info("Starter LagreForespoerselLoeser...")
        LagreForespoerselLoeser(this, forespoerselRepo)
        logger.info("Starter PersisterImLøser...")
        PersisterImLøser(this, imRepo)
        logger.info("Starter HentPersistertLøser...")
        HentPersistertLøser(this, imRepo)
        logger.info("Starter LagreJournalpostIdLøser...")
        LagreJournalpostIdLøser(this, imRepo)
        logger.info("Starter PersisterSakLøser...")
        PersisterSakLøser(this, forespoerselRepo)
        logger.info("Starter PersisterOppgaveLøser...")
        PersisterOppgaveLøser(this, forespoerselRepo)
        logger.info("Starter HentOrgnrLoeser...")
        HentOrgnrLoeser(this, forespoerselRepo)
        logger.info("Starter NotifikasjonHentIdLoeser...")
        NotifikasjonHentIdLoeser(this, forespoerselRepo)

        registerDbLifecycle(database)
    }

private fun RapidsConnection.registerDbLifecycle(db: Database) {
    register(object : RapidsConnection.StatusListener {
        override fun onShutdown(rapidsConnection: RapidsConnection) {
            logger.info("Mottatt stoppsignal, lukker databasetilkobling")
            db.dataSource.close()
        }
    })
}
