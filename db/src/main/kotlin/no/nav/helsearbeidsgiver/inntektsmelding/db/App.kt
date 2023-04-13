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
    val imRepo = InntektsmeldingRepository(database.db)
    val forespoerselRepo = ForespoerselRepository(database.db)
    RapidApplication
        .create(env)
        .createDb(database, imRepo, forespoerselRepo)
        .start()
}

fun RapidsConnection.createDb(database: Database, imRepo: InntektsmeldingRepository, forespoerselRepo: ForespoerselRepository): RapidsConnection {
    logger.info("Starter ForespørselMottattListener...")
    ForespørselMottattListener(this, forespoerselRepo)
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
