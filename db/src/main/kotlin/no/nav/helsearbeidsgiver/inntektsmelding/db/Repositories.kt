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

class ForespoerselRepository(private val db: Database) {

    fun oppdaterOppgaveId(forespørselId: String, oppgaveId: String) {
        transaction(db) {
            ForespoerselEntitet.update({ (ForespoerselEntitet.forespoerselId eq forespørselId) and (ForespoerselEntitet.oppgaveId eq null) }) {
                it[ForespoerselEntitet.oppgaveId] = oppgaveId
            }
        }
    }

    fun oppdaterSakId(sakId: String, forespørselId: String) {
        transaction(db) {
            ForespoerselEntitet.update({ (ForespoerselEntitet.forespoerselId eq forespørselId) and (ForespoerselEntitet.sakId eq null) }) {
                it[ForespoerselEntitet.sakId] = sakId
            }
        }
    }

    fun hentOppgaveId(forespørselId: String): String? =
        transaction(db) {
            ForespoerselEntitet.run {
                select { (forespoerselId eq forespørselId) }
            }.firstOrNull()?.getOrNull(ForespoerselEntitet.oppgaveId)
        }

    fun hentSakId(forespørselId: String): String? =
        transaction(db) {
            ForespoerselEntitet.run {
                select { (forespoerselId eq forespørselId) }
            }.firstOrNull()?.getOrNull(ForespoerselEntitet.sakId)
        }

    fun hentOrgNr(forespørselId: String): String? =
        transaction(db) {
            ForespoerselEntitet.run {
                select { (forespoerselId eq forespørselId) }.firstOrNull()?.getOrNull(orgnr)
            }
        }

    fun lagreForespørsel(forespørselId: String, organisasjonsnummer: String) {
        transaction(db) {
            ForespoerselEntitet.run {
                insert {
                    it[forespoerselId] = forespørselId
                    it[orgnr] = organisasjonsnummer
                    it[opprettet] = LocalDateTime.now()
                }
            }
        }
    }
}

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

    fun oppdaterJournapostId(journalpostId: String, uuid: String) {
        transaction(db) {
            InntektsmeldingEntitet.update({ (InntektsmeldingEntitet.forespoerselId eq uuid) and (InntektsmeldingEntitet.journalpostId eq null) }) {
                it[InntektsmeldingEntitet.journalpostId] = journalpostId
            }
        }
    }
}
