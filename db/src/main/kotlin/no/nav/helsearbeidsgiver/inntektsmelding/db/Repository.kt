package no.nav.helsearbeidsgiver.inntektsmelding.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

class Repository(private val db: Database) {

    fun lagre(uuidLink: String, json: no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument): Unit =
        transaction(db) {
            InntektsmeldingEntitet.update({ (InntektsmeldingEntitet.uuid eq uuidLink) and (InntektsmeldingEntitet.dokument eq null) }) {
                it[dokument] = json
                it[innsendt] = LocalDateTime.now()
            }
        }

    fun hentNyeste(uuidLink: String): no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument? =
        transaction(db) {
            InntektsmeldingEntitet.run {
                select { (uuid eq uuidLink) }.orderBy(opprettet, SortOrder.DESC)
            }.take(100).first().getOrNull(InntektsmeldingEntitet.dokument)
        }

    fun oppdaterJournapostId(journalpostId: String, uuid: String) {
        transaction(db) {
            InntektsmeldingEntitet.update({ (InntektsmeldingEntitet.uuid eq uuid) and (InntektsmeldingEntitet.journalpostId eq null) }) {
                it[InntektsmeldingEntitet.journalpostId] = journalpostId
            }
        }
    }

    fun oppdaterOppgaveId(oppgaveId: String, uuid: String) {
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

    fun lagreForespørsel(uuidLink: String, fnr: String, orgnr: String) {
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
