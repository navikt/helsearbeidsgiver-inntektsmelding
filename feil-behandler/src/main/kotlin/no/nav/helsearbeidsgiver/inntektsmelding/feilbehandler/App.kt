package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler

import com.zaxxer.hikari.HikariConfig
import no.nav.hag.utils.bakgrunnsjobb.PostgresBakgrunnsjobbRepository
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.config.Database
import no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.config.DatabaseConfig
import no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.config.mapHikariConfig
import no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.river.FeilLytter
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

private val logger = "helsearbeidsgiver-im-feil-behandler".logger()
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
    return RapidApplication
        .create(env)
        .createFeilLytter(database)
}

fun RapidsConnection.createFeilLytter(database: Database): RapidsConnection =
    also {
        registerDbLifecycle(database)
        val repository = PostgresBakgrunnsjobbRepository(database.dataSource)
        FeilLytter(it, repository)
    }

private fun RapidsConnection.registerDbLifecycle(db: Database) {
    register(object : RapidsConnection.StatusListener {
        override fun onShutdown(rapidsConnection: RapidsConnection) {
            logger.info("Mottatt stoppsignal, lukker databasetilkobling")
            db.dataSource.close()
        }
    })
}
