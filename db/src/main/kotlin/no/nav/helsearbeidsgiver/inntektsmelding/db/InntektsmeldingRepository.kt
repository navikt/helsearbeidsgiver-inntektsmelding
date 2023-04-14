package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

class InntektsmeldingRepository(private val db: Database) {

    fun lagreInntektsmeldng(forespørselId: String, inntektsmeldingDokument: InntektsmeldingDokument) {
        transaction(db) {
            InntektsmeldingEntitet.run {
                insert {
                    it[forespoerselId] = forespørselId
                    it[dokument] = inntektsmeldingDokument
                    it[innsendt] = LocalDateTime.now()
                }
            }
        }
    }
    fun hentNyeste(forespørselId: String): InntektsmeldingDokument? =
        transaction(db) {
            InntektsmeldingEntitet.run {
                select { (forespoerselId eq forespørselId) }.orderBy(innsendt, SortOrder.DESC)
            }.firstOrNull()?.getOrNull(InntektsmeldingEntitet.dokument)
        }

    fun oppdaterJournapostId(journalpostId: String, forespørselId: String) {
        transaction(db) {
            InntektsmeldingEntitet.update({ (InntektsmeldingEntitet.forespoerselId eq forespørselId) and (InntektsmeldingEntitet.journalpostId eq null) }) {
                it[InntektsmeldingEntitet.journalpostId] = journalpostId
            }
        }
    }
}
