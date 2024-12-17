package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.db.exposed.firstOrNull
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.inntektsmelding.db.tabell.SelvbestemtInntektsmeldingEntitet
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

class SelvbestemtImRepo(
    private val db: Database,
) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    fun hentNyesteIm(selvbestemtId: UUID): Inntektsmelding? =
        Metrics.dbSelvbestemtIm.recordTime(::hentNyesteIm) {
            transaction(db) {
                SelvbestemtInntektsmeldingEntitet
                    .selectAll()
                    .where { SelvbestemtInntektsmeldingEntitet.selvbestemtId eq selvbestemtId }
                    .orderBy(SelvbestemtInntektsmeldingEntitet.opprettet, SortOrder.DESC)
                    .limit(1)
                    .firstOrNull(SelvbestemtInntektsmeldingEntitet.inntektsmelding)
            }
        }

    fun lagreIm(im: Inntektsmelding) {
        Metrics.dbSelvbestemtIm.recordTime(::lagreIm) {
            transaction(db) {
                SelvbestemtInntektsmeldingEntitet.insert {
                    it[inntektsmeldingId] = im.id
                    it[selvbestemtId] = im.type.id
                    it[inntektsmelding] = im
                }
            }
        }
    }

    fun oppdaterJournalpostId(
        inntektsmeldingId: UUID,
        journalpostId: String,
    ) {
        val antallOppdatert =
            Metrics.dbSelvbestemtIm.recordTime(::oppdaterJournalpostId) {
                transaction(db) {
                    SelvbestemtInntektsmeldingEntitet.update(
                        where = {
                            (SelvbestemtInntektsmeldingEntitet.inntektsmeldingId eq inntektsmeldingId) and
                                SelvbestemtInntektsmeldingEntitet.journalpostId.isNull()
                        },
                    ) {
                        it[this.journalpostId] = journalpostId
                    }
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
