package no.nav.helsearbeidsgiver.inntektsmelding.db

import org.jetbrains.exposed.sql.Database
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
