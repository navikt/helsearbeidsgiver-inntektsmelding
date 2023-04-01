package no.nav.helsearbeidsgiver.inntektsmelding.db

import com.zaxxer.hikari.HikariConfig
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerLogger: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-db")

fun main() {
    buildApp(mapHikariConfig(DatabaseConfig()), System.getenv())
}

fun buildApp(config: HikariConfig, env: Map<String, String>) {
    val database = Database(config)
    logger.info("Bruker database url: ${config.jdbcUrl}")
    logger.info("Migrering starter...")
    database.migrate()
    logger.info("Migrering ferdig.")
    val repository = Repository(database.db)
    RapidApplication
        .create(env)
        .createDb(database, repository)
        .start()
}

fun RapidsConnection.createDb(database: Database, repository: Repository): RapidsConnection {
    logger.info("Starter ForespørselMottattListener...")
    ForespørselMottattListener(this, repository)
    logger.info("Starter PersisterImLøser...")
    PersisterImLøser(this, repository)
    logger.info("Starter HentPersistertLøser...")
    HentPersistertLøser(this, repository)
    logger.info("Starter LagreJournalpostIdLøser...")
    LagreJournalpostIdLøser(this, repository)
    logger.info("Starter PersisterSakLøser...")
    PersisterSakLøser(this, repository)
    logger.info("Starter PersisterOppgaveLøser...")
    PersisterOppgaveLøser(this, repository)
    HentOrgnrLøser(this, repository)
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
