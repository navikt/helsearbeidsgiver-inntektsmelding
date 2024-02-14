package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.db.exposed.Database
import no.nav.helsearbeidsgiver.felles.rapidsrivers.registerShutdownLifecycle
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.HentAapenImRiver
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.HentOrgnrLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.HentPersistertLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreAapenImRiver
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreEksternInntektsmeldingLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreForespoerselLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.LagreJournalpostIdLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.NotifikasjonHentIdLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.PersisterImLoeser
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
    val aapenImRepo = AapenImRepo(database.db)
    val forespoerselRepo = ForespoerselRepository(database.db)

    return RapidApplication
        .create(System.getenv())
        .createDbRivers(imRepo, aapenImRepo, forespoerselRepo)
        .registerShutdownLifecycle {
            logger.info("Stoppsignal mottatt, lukker databasetilkobling.")
            database.dataSource.close()
        }
        .start()
}

fun RapidsConnection.createDbRivers(
    imRepo: InntektsmeldingRepository,
    aapenImRepo: AapenImRepo,
    forespoerselRepo: ForespoerselRepository
): RapidsConnection =
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

        logger.info("Starter ${HentAapenImRiver::class.simpleName}...")
        HentAapenImRiver(aapenImRepo).connect(this)

        logger.info("Starter ${LagreAapenImRiver::class.simpleName}...")
        LagreAapenImRiver(aapenImRepo).connect(this)
    }
