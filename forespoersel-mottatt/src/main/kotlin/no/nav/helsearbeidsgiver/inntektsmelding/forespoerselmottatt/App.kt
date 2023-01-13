package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.log.logger
import no.nav.helsearbeidsgiver.felles.log.loggerSikker
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt.db.Database

val logger = "im-forespoersel-mottatt".logger()
val loggerSikker = loggerSikker()

fun main() {
    logger.info("Jeg er oppe og kjører!")

    val database = Database()
    val forespoerselDao = ForespoerselDao(database.db)

    RapidApplication.create(System.getenv()).also {
        ForespoerselMottattLøser(it, forespoerselDao)

        it.registerDbLifecycle(database)
        it.start()
    }

    logger.info("Hasta la vista, baby!")
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
