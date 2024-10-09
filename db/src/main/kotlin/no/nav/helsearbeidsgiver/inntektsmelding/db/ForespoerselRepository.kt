package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.felles.db.exposed.firstOrNull
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.inntektsmelding.db.tabell.ForespoerselEntitet
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

class ForespoerselRepository(
    private val db: Database,
) {
    fun oppdaterOppgaveId(
        forespoerselId: String,
        oppgaveId: String,
    ) {
        Metrics.dbForespoersel.recordTime(::oppdaterOppgaveId) {
            transaction(db) {
                ForespoerselEntitet.update(
                    where = {
                        (ForespoerselEntitet.forespoerselId eq forespoerselId) and
                            ForespoerselEntitet.oppgaveId.isNull()
                    },
                ) {
                    it[ForespoerselEntitet.oppgaveId] = oppgaveId
                }
            }
        }
    }

    fun oppdaterSakId(
        forespoerselId: String,
        sakId: String,
    ) {
        Metrics.dbForespoersel.recordTime(::oppdaterSakId) {
            transaction(db) {
                ForespoerselEntitet.update(
                    where = {
                        (ForespoerselEntitet.forespoerselId eq forespoerselId) and
                            ForespoerselEntitet.sakId.isNull()
                    },
                ) {
                    it[ForespoerselEntitet.sakId] = sakId
                }
            }
        }
    }

    fun hentOppgaveId(forespoerselId: UUID): String? =
        Metrics.dbForespoersel.recordTime(::hentOppgaveId) {
            transaction(db) {
                ForespoerselEntitet
                    .selectAll()
                    .where { ForespoerselEntitet.forespoerselId eq forespoerselId.toString() }
                    .firstOrNull(ForespoerselEntitet.oppgaveId)
            }
        }

    fun hentSakId(forespoerselId: UUID): String? =
        Metrics.dbForespoersel.recordTime(::hentSakId) {
            transaction(db) {
                ForespoerselEntitet
                    .selectAll()
                    .where { ForespoerselEntitet.forespoerselId eq forespoerselId.toString() }
                    .firstOrNull(ForespoerselEntitet.sakId)
            }
        }

    fun lagreForespoersel(
        forespoerselId: String,
        organisasjonsnummer: String,
    ) {
        Metrics.dbForespoersel.recordTime(::lagreForespoersel) {
            transaction(db) {
                ForespoerselEntitet.insert {
                    it[this.forespoerselId] = forespoerselId
                    it[orgnr] = organisasjonsnummer
                    it[opprettet] = LocalDateTime.now()
                }
            }
        }
    }
}
