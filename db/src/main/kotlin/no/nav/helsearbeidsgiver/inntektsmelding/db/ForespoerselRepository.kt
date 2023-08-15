package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.inntektsmelding.db.config.ForespoerselEntitet
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

class ForespoerselRepository(private val db: Database) {

    fun oppdaterOppgaveId(forespoerselId: String, oppgaveId: String) {
        transaction(db) {
            ForespoerselEntitet.update({ (ForespoerselEntitet.forespoerselId eq forespoerselId) and (ForespoerselEntitet.oppgaveId eq null) }) {
                it[ForespoerselEntitet.oppgaveId] = oppgaveId
            }
        }
    }

    fun oppdaterSakId(forespoerselId: String, sakId: String) {
        transaction(db) {
            ForespoerselEntitet.update({ (ForespoerselEntitet.forespoerselId eq forespoerselId) and (ForespoerselEntitet.sakId eq null) }) {
                it[ForespoerselEntitet.sakId] = sakId
            }
        }
    }

    fun hentOppgaveId(forespoerselId: String): String? =
        transaction(db) {
            ForespoerselEntitet.let {
                it.select { (it.forespoerselId eq forespoerselId) }
            }
                .firstOrNull(ForespoerselEntitet.oppgaveId)
        }

    fun hentSakId(forespoerselId: String): String? =
        transaction(db) {
            ForespoerselEntitet.let {
                it.select { (it.forespoerselId eq forespoerselId) }
            }
                .firstOrNull(ForespoerselEntitet.sakId)
        }

    fun hentOrgnr(forespoerselId: UUID): String? =
        transaction(db) {
            ForespoerselEntitet.let {
                it.select { (it.forespoerselId eq forespoerselId.toString()) }
                    .firstOrNull(it.orgnr)
            }
        }

    fun lagreForespoersel(forespoerselId: String, organisasjonsnummer: String) {
        transaction(db) {
            ForespoerselEntitet.run {
                insert {
                    it[this.forespoerselId] = forespoerselId
                    it[orgnr] = organisasjonsnummer
                    it[opprettet] = LocalDateTime.now()
                }
            }
        }
    }
}

private fun <T> Query.firstOrNull(c: Expression<T>): T? =
    firstOrNull()?.getOrNull(c)
