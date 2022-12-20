package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.log.logger
import no.nav.helsearbeidsgiver.felles.log.loggerSikker

val logger = "im-forespoersel-mottatt".logger()
val loggerSikker = loggerSikker()

fun main() {
    logger.info("Jeg er oppe og kjører!")

//    val forespoerselDao = ForespoerselDao(Db.db)

    RapidApplication.create(System.getenv()).also {
        ForespoerselMottattLøser(it)

//        it.registerDbLifecycle()
        it.start()
    }

    logger.info("Hasta la vista, baby!")
}

// private fun RapidsConnection.registerDbLifecycle() {
//    register(object : RapidsConnection.StatusListener {
//        override fun onStartup(rapidsConnection: RapidsConnection) {
//            Db.migrate()
//        }
//        override fun onShutdown(rapidsConnection: RapidsConnection) {
//            Db.dataSource.close()
//        }
//    })
// }
