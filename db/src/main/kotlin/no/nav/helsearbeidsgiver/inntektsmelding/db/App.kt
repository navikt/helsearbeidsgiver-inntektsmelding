package no.nav.helsearbeidsgiver.inntektsmelding.db

import com.zaxxer.hikari.HikariConfig
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.Database
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.DatabaseConfig
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.mapHikariConfig
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.HentOrgnrLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.HentPersistertLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreEksternInntektsmeldingLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreForespoerselLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreJournalpostIdLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.NotifikasjonHentIdLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.PersisterImLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.PersisterOppgaveLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.PersisterSakLoeser
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
    also {
        logger.info("Starter ${LagreForespoerselLoeser::class.simpleName}...")
        LagreForespoerselLoeser(this, forespoerselRepo)

        logger.info("Starter ${PersisterImLoeser::class.simpleName}...")
        PersisterImLoeser(this, imRepo)

        logger.info("Starter ${HentPersistertLoeser::class.simpleName}...")
        HentPersistertLoeser(this, imRepo)

        logger.info("Starter ${LagreJournalpostIdLoeser::class.simpleName}...")
        LagreJournalpostIdLoeser(this, imRepo)

        logger.info("Starter ${PersisterSakLoeser::class.simpleName}...")
        PersisterSakLoeser(this, forespoerselRepo)

        logger.info("Starter ${PersisterOppgaveLoeser::class.simpleName}...")
        PersisterOppgaveLoeser(this, forespoerselRepo)

        logger.info("Starter ${HentOrgnrLoeser::class.simpleName}...")
        HentOrgnrLoeser(this, forespoerselRepo)

        logger.info("Starter ${NotifikasjonHentIdLoeser::class.simpleName}...")
        NotifikasjonHentIdLoeser(this, forespoerselRepo)

        logger.info("Starter ${LagreEksternInntektsmeldingLoeser::class.simpleName}...")
        LagreEksternInntektsmeldingLoeser(this, imRepo)

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
