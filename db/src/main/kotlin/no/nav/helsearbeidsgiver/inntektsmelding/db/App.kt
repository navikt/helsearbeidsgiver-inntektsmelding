package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerLogger: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-db")

fun main() {
    val database = Database()
    val repository = Repository(database.db)
    RapidApplication
        .create(System.getenv())
        .createDb(database, repository)
        .start()
}

fun RapidsConnection.createDb(database: Database, repository: Repository): RapidsConnection {
    sikkerLogger.info("Starter Flyway migrering...")
    this.registerDbLifecycle(database)
    sikkerLogger.info("Starter PersisterImLøser...")
    PersisterImLøser(this, repository)
    sikkerLogger.info("Starter HentPersistertLøser...")
    HentPersistertLøser(this, repository)
    sikkerLogger.info("Starter LagreJournalpostIdLøser...")
    LagreJournalpostIdLøser(this, repository)
    return this
}

private fun RapidsConnection.registerDbLifecycle(db: Database) {
    register(object : RapidsConnection.StatusListener {
        override fun onStartup(rapidsConnection: RapidsConnection) {
            db.migrate()
        }

        override fun onShutdown(rapidsConnection: RapidsConnection) {
            db.dataSource.close()
        }
    })
}
