package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.inntektsmelding.db.tabell.InntektsmeldingEntitet
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

class InntektsmeldingRepository(
    private val db: Database,
) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    fun hentNyesteEksternEllerInternInntektsmelding(forespoerselId: UUID): Triple<SkjemaInntektsmelding?, Inntektsmelding?, EksternInntektsmelding?> =
        Metrics.dbInntektsmelding.recordTime(InntektsmeldingRepository::hentNyesteEksternEllerInternInntektsmelding) {
            transaction(db) {
                InntektsmeldingEntitet
                    .select(
                        InntektsmeldingEntitet.skjema,
                        InntektsmeldingEntitet.dokument,
                        InntektsmeldingEntitet.eksternInntektsmelding,
                    ).where { InntektsmeldingEntitet.forespoerselId eq forespoerselId.toString() }
                    .orderBy(InntektsmeldingEntitet.innsendt, SortOrder.DESC)
                    .limit(1)
                    .map {
                        Triple(
                            it[InntektsmeldingEntitet.skjema],
                            it[InntektsmeldingEntitet.dokument],
                            it[InntektsmeldingEntitet.eksternInntektsmelding],
                        )
                    }.firstOrNull()
            }.orDefault(Triple(null, null, null))
        }

    fun oppdaterJournalpostId(
        innsendingId: Long,
        journalpostId: String,
    ) {
        Metrics.dbInntektsmelding.recordTime(InntektsmeldingRepository::oppdaterJournalpostId) {
            val antallOppdatert =
                transaction(db) {
                    InntektsmeldingEntitet.update(
                        where = { InntektsmeldingEntitet.id eq innsendingId },
                    ) {
                        it[InntektsmeldingEntitet.journalpostId] = journalpostId
                    }
                }

            if (antallOppdatert == 1) {
                "Lagret journalpost-ID '$journalpostId' i database.".also {
                    logger.info(it)
                    sikkerLogger.info(it)
                }
            } else {
                "Oppdaterte uventet antall ($antallOppdatert) rader med journalpost-ID '$journalpostId'.".also {
                    logger.error(it)
                    sikkerLogger.error(it)
                }
            }
        }
    }

    fun lagreEksternInntektsmelding(
        forespoerselId: UUID,
        eksternIm: EksternInntektsmelding,
    ) {
        transaction(db) {
            InntektsmeldingEntitet.insert {
                it[this.forespoerselId] = forespoerselId.toString()
                it[eksternInntektsmelding] = eksternIm
                it[innsendt] = LocalDateTime.now()
            }
        }
    }

    fun hentNyesteBerikedeInnsendingId(forespoerselId: UUID): Long? =
        Metrics.dbInntektsmelding.recordTime(InntektsmeldingRepository::hentNyesteBerikedeInnsendingId) {
            transaction(db) {
                hentNyesteImQuery(forespoerselId)
                    .firstOrNull()
                    ?.getOrNull(InntektsmeldingEntitet.id)
            }
        }

    fun lagreInntektsmeldingSkjema(
        inntektsmeldingSkjema: SkjemaInntektsmelding,
        mottatt: LocalDateTime,
    ): Long =
        Metrics.dbInntektsmelding.recordTime(InntektsmeldingRepository::lagreInntektsmeldingSkjema) {
            transaction(db) {
                InntektsmeldingEntitet.insert {
                    it[this.forespoerselId] = inntektsmeldingSkjema.forespoerselId.toString()
                    it[skjema] = inntektsmeldingSkjema
                    it[innsendt] = mottatt
                } get InntektsmeldingEntitet.id
            }
        }

    fun hentNyesteInntektsmeldingSkjema(forespoerselId: UUID): SkjemaInntektsmelding? =
        Metrics.dbInntektsmelding.recordTime(InntektsmeldingRepository::hentNyesteInntektsmeldingSkjema) {
            transaction(db) {
                hentNyesteImSkjemaQuery(forespoerselId)
                    .firstOrNull()
                    ?.getOrNull(InntektsmeldingEntitet.skjema)
            }
        }

    fun oppdaterMedBeriketDokument(
        forespoerselId: UUID,
        innsendingId: Long,
        inntektsmeldingDokument: Inntektsmelding,
    ) {
        val antallOppdatert =
            Metrics.dbInntektsmelding.recordTime(InntektsmeldingRepository::oppdaterMedBeriketDokument) {
                transaction(db) {
                    InntektsmeldingEntitet.update(
                        where = {
                            InntektsmeldingEntitet.id eq innsendingId
                        },
                    ) {
                        it[dokument] = inntektsmeldingDokument
                    }
                }
            }

        if (antallOppdatert == 1) {
            "Lagret inntektsmelding for forespørsel-ID $forespoerselId i database.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }
        } else {
            "Oppdaterte uventet antall ($antallOppdatert) rader ved lagring av inntektsmelding med forespørsel-ID $forespoerselId.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }
    }

    private fun hentNyesteImQuery(forespoerselId: UUID): Query =
        InntektsmeldingEntitet
            .selectAll()
            .where { (InntektsmeldingEntitet.forespoerselId eq forespoerselId.toString()) and InntektsmeldingEntitet.dokument.isNotNull() }
            .orderBy(InntektsmeldingEntitet.innsendt, SortOrder.DESC)
            .limit(1)

    private fun hentNyesteImSkjemaQuery(forespoerselId: UUID): Query =
        InntektsmeldingEntitet
            .selectAll()
            .where { InntektsmeldingEntitet.forespoerselId eq forespoerselId.toString() }
            .orderBy(InntektsmeldingEntitet.innsendt, SortOrder.DESC)
            .limit(1)
}
