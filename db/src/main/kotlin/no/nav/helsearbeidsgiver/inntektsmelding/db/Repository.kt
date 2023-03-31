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

class Repository(private val db: Database) {

    fun oppdaterDokument(uuidLink: String, json: InntektsmeldingDokument): Unit =
        transaction(db) {
            InntektsmeldingEntitet.update({ (InntektsmeldingEntitet.uuid eq uuidLink) and (InntektsmeldingEntitet.dokument eq null) }) {
                it[dokument] = json
                it[innsendt] = LocalDateTime.now()
            }
        }

    fun hentSakId(uuidLink: String): String? =
        transaction(db) {
            InntektsmeldingEntitet.run {
                select { (uuid eq uuidLink) }.orderBy(opprettet, SortOrder.DESC)
            }.take(1).first().getOrNull(InntektsmeldingEntitet.sakId)
        }

    fun hentOppgaveId(uuidLink: String): String? =
        transaction(db) {
            InntektsmeldingEntitet.run {
                select { (uuid eq uuidLink) }.orderBy(opprettet, SortOrder.DESC)
            }.take(1).first().getOrNull(InntektsmeldingEntitet.oppgaveId)
        }

    fun hentNyeste(uuidLink: String): InntektsmeldingDokument? =
        transaction(db) {
            InntektsmeldingEntitet.run {
                select { (uuid eq uuidLink) }.orderBy(opprettet, SortOrder.DESC)
            }.take(1).first().getOrNull(InntektsmeldingEntitet.dokument)
        }

    fun oppdaterJournapostId(journalpostId: String, uuid: String) {
        transaction(db) {
            InntektsmeldingEntitet.update({ (InntektsmeldingEntitet.uuid eq uuid) and (InntektsmeldingEntitet.journalpostId eq null) }) {
                it[InntektsmeldingEntitet.journalpostId] = journalpostId
            }
        }
    }

    fun oppdaterOppgaveId(uuid: String, oppgaveId: String) {
        transaction(db) {
            InntektsmeldingEntitet.update({ (InntektsmeldingEntitet.uuid eq uuid) and (InntektsmeldingEntitet.oppgaveId eq null) }) {
                it[InntektsmeldingEntitet.oppgaveId] = oppgaveId
            }
        }
    }

    fun oppdaterSakId(sakId: String, uuid: String) {
        transaction(db) {
            InntektsmeldingEntitet.update({ (InntektsmeldingEntitet.uuid eq uuid) and (InntektsmeldingEntitet.sakId eq null) }) {
                it[InntektsmeldingEntitet.sakId] = sakId
            }
        }
    }

    fun lagreForespørsel(uuidLink: String) {
        transaction(db) {
            InntektsmeldingEntitet.run {
                insert {
                    it[uuid] = uuidLink
                    it[opprettet] = LocalDateTime.now()
                }
            }
        }
    }
}
