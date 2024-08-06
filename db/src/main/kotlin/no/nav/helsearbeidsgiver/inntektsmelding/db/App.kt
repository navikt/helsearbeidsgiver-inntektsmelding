package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.db.exposed.Database
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.HentLagretImRiver
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.HentSelvbestemtImRiver
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreEksternInntektsmeldingLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreForespoerselLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreImRiver
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreImSkjemaRiver
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreJournalpostIdRiver
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreSelvbestemtImRiver
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.NotifikasjonHentIdLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.PersisterOppgaveLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.PersisterSakLoeser
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-db".logger()

fun main() {
    val database = Database("NAIS_DATABASE_IM_DB_INNTEKTSMELDING")

    logger.info("Migrering starter...")
    database.migrate()
    logger.info("Migrering ferdig.")

    val imRepo = InntektsmeldingRepository(database.db)
    val selvbestemtImRepo = SelvbestemtImRepo(database.db)
    val forespoerselRepo = ForespoerselRepository(database.db)

    return RapidApplication
        .create(System.getenv())
        .createDbRivers(imRepo, selvbestemtImRepo, forespoerselRepo)
        .registerShutdownLifecycle {
            logger.info("Stoppsignal mottatt, lukker databasetilkobling.")
            database.dataSource.close()
        }.start()
}

fun RapidsConnection.createDbRivers(
    imRepo: InntektsmeldingRepository,
    selvbestemtImRepo: SelvbestemtImRepo,
    forespoerselRepo: ForespoerselRepository,
): RapidsConnection =
    also {
        logger.info("Starter ${LagreForespoerselLoeser::class.simpleName}...")
        LagreForespoerselLoeser(this, forespoerselRepo)

        logger.info("Starter ${LagreImSkjemaRiver::class.simpleName}...")
        LagreImSkjemaRiver(imRepo).connect(this)

        logger.info("Starter ${HentLagretImRiver::class.simpleName}...")
        HentLagretImRiver(imRepo).connect(this)

        logger.info("Starter ${LagreImRiver::class.simpleName}...")
        LagreImRiver(imRepo).connect(this)

        logger.info("Starter ${LagreJournalpostIdRiver::class.simpleName}...")
        LagreJournalpostIdRiver(imRepo, selvbestemtImRepo).connect(this)

        logger.info("Starter ${PersisterSakLoeser::class.simpleName}...")
        PersisterSakLoeser(this, forespoerselRepo)

        logger.info("Starter ${PersisterOppgaveLoeser::class.simpleName}...")
        PersisterOppgaveLoeser(this, forespoerselRepo)

        logger.info("Starter ${NotifikasjonHentIdLoeser::class.simpleName}...")
        NotifikasjonHentIdLoeser(this, forespoerselRepo)

        logger.info("Starter ${LagreEksternInntektsmeldingLoeser::class.simpleName}...")
        LagreEksternInntektsmeldingLoeser(this, imRepo)

        logger.info("Starter ${HentSelvbestemtImRiver::class.simpleName}...")
        HentSelvbestemtImRiver(selvbestemtImRepo).connect(this)

        logger.info("Starter ${LagreSelvbestemtImRiver::class.simpleName}...")
        LagreSelvbestemtImRiver(selvbestemtImRepo).connect(this)
    }
