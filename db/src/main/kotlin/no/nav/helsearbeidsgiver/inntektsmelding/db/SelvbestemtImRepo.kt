package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.db.exposed.firstOrNull
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.metrics.recordTime
import no.nav.helsearbeidsgiver.inntektsmelding.db.tabell.SelvbestemtInntektsmeldingEntitet
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

// TODO test
class SelvbestemtImRepo(private val db: Database) {

    fun hentNyesteIm(selvbestemtId: UUID): Inntektsmelding? =
        Metrics.dbSelvbestemtIm.recordTime(::hentNyesteIm) {
            transaction(db) {
                hentNyesteImQuery(selvbestemtId)
                    .firstOrNull(SelvbestemtInntektsmeldingEntitet.inntektsmelding)
            }
        }

    fun lagreIm(im: Inntektsmelding) {
        Metrics.dbSelvbestemtIm.recordTime(::lagreIm) {
            transaction(db) {
                SelvbestemtInntektsmeldingEntitet.insert {
                    it[inntektsmeldingId] = UUID.randomUUID()
                    it[selvbestemtId] = im.id
                    it[inntektsmelding] = im
                }
            }
        }
    }

    fun oppdaterJournalpostId(selvbestemtId: UUID, journalpostId: String) {
        Metrics.dbSelvbestemtIm.recordTime(::oppdaterJournalpostId) {
            transaction(db) {
                SelvbestemtInntektsmeldingEntitet.update(
                    where = {
                        val nyesteImIdQuery = hentNyesteImQuery(selvbestemtId).adjustSelect { select(SelvbestemtInntektsmeldingEntitet.id) }

                        (SelvbestemtInntektsmeldingEntitet.id eqSubQuery nyesteImIdQuery) and
                            SelvbestemtInntektsmeldingEntitet.journalpostId.isNull()
                    }
                ) {
                    it[this.journalpostId] = journalpostId
                }
            }
        }
    }
}

private fun hentNyesteImQuery(selvbestemtId: UUID): Query =
    SelvbestemtInntektsmeldingEntitet
        .selectAll()
        .where { SelvbestemtInntektsmeldingEntitet.selvbestemtId eq selvbestemtId }
        .orderBy(SelvbestemtInntektsmeldingEntitet.opprettet, SortOrder.DESC)
        .limit(1)
