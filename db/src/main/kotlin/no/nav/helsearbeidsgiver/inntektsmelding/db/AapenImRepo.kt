package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.db.exposed.firstOrNull
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.metrics.recordTime
import no.nav.helsearbeidsgiver.inntektsmelding.db.tabell.AapenInntektsmeldingEntitet
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
class AapenImRepo(private val db: Database) {

    fun hentNyesteIm(aapenId: UUID): Inntektsmelding? =
        Metrics.dbAapenIm.recordTime(::hentNyesteIm.name) {
            transaction(db) {
                hentNyesteImQuery(aapenId)
                    .firstOrNull(AapenInntektsmeldingEntitet.inntektsmelding)
            }
        }

    fun lagreIm(im: Inntektsmelding) {
        Metrics.dbAapenIm.recordTime(::lagreIm.name) {
            transaction(db) {
                AapenInntektsmeldingEntitet.insert {
                    it[aapenId] = im.id
                    it[inntektsmelding] = im
                }
            }
        }
    }

    fun oppdaterJournalpostId(aapenId: UUID, journalpostId: String) {
        Metrics.dbAapenIm.recordTime(::oppdaterJournalpostId.name) {
            transaction(db) {
                AapenInntektsmeldingEntitet.update(
                    where = {
                        val nyesteImIdQuery = hentNyesteImQuery(aapenId).adjustSelect { select(AapenInntektsmeldingEntitet.id) }

                        (AapenInntektsmeldingEntitet.id eqSubQuery nyesteImIdQuery) and
                            AapenInntektsmeldingEntitet.journalpostId.isNull()
                    }
                ) {
                    it[this.journalpostId] = journalpostId
                }
            }
        }
    }
}

private fun hentNyesteImQuery(aapenId: UUID): Query =
    AapenInntektsmeldingEntitet
        .selectAll()
        .where { AapenInntektsmeldingEntitet.aapenId eq aapenId }
        .orderBy(AapenInntektsmeldingEntitet.opprettet, SortOrder.DESC)
        .limit(1)
