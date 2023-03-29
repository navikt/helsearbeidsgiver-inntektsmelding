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
    val databaseFactory = DatabaseFactory(config)
    logger.info("Bruker database url: ${config.jdbcUrl}")
    val repository = Repository(databaseFactory.db)
    RapidApplication
        .create(env)
        .setupDatabase(databaseFactory, repository)
        .start()
}

fun RapidsConnection.setupDatabase(databaseFactory: DatabaseFactory, repository: Repository): RapidsConnection {
    logger.info("Starter Flyway migrering...")
    databaseFactory.migrate()
    this.registerDbLifecycle(databaseFactory)
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
    return this
}

private fun RapidsConnection.registerDbLifecycle(databaseFactory: DatabaseFactory) {
    register(object : RapidsConnection.StatusListener {

        override fun onShutdown(rapidsConnection: RapidsConnection) {
            databaseFactory.dataSource.close()
        }
    })
}
