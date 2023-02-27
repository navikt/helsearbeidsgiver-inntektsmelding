package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-db")

fun main() {
    logger.info("Starting im-db...")
    val database = Database()
   // val repository = Repository(database.db)
    RapidApplication.create(System.getenv()).also {
       // it.registerDbLifecycle(database)
        PersisterImLøser(it)
        /*
        logger.info("Registrerte db lifecycle")
        PersisterImLøser(it, repository)
        logger.info("Startet PersisterImLøser")
        HentPersistertLøser(it, repository)
        logger.info("Startet HentPersistertLøser")
        LagreJournalpostIdLøser(it, repository)
        logger.info("Startet LagreJournalpostIdLøser")

         */
        it.start()
    }
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
