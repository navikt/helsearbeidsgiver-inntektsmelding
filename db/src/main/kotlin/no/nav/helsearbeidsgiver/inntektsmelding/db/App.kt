package no.nav.helsearbeidsgiver.inntektsmelding.db

import com.zaxxer.hikari.HikariConfig
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
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
    val rapid = RapidApplication
        .create(env)
        .createDb(database, imRepo, forespoerselRepo)
    return rapid
}

fun RapidsConnection.createDb(database: Database, imRepo: InntektsmeldingRepository, forespoerselRepo: ForespoerselRepository): RapidsConnection {
    logger.info("Starter ForespørselMottattListener...")
    LagreForespoersel(this, forespoerselRepo)
    logger.info("Starter PersisterImLøser...")
    PersisterImLøser(this, imRepo)
    logger.info("Starter HentPersistertLøser...")
    HentPersistertLøser(this, imRepo)
    logger.info("Starter LagreJournalpostIdLøser...")
    LagreJournalpostIdLøser(this, imRepo, forespoerselRepo)
    logger.info("Starter PersisterSakLøser...")
    PersisterSakLøser(this, forespoerselRepo)
    logger.info("Starter PersisterOppgaveLøser...")
    PersisterOppgaveLøser(this, forespoerselRepo)
    HentOrgnrLøser(this, forespoerselRepo)
    this.registerDbLifecycle(database)
    return this
}

private fun RapidsConnection.registerDbLifecycle(db: Database) {
    register(object : RapidsConnection.StatusListener {

        override fun onShutdown(rapidsConnection: RapidsConnection) {
            logger.info("Mottatt stoppsignal, lukker databasetilkobling")
            db.dataSource.close()
        }
    })
}
