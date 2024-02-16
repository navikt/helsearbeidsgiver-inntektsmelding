package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.prometheus.client.Summary
import no.nav.helsearbeidsgiver.felles.db.exposed.firstOrNull
import no.nav.helsearbeidsgiver.inntektsmelding.db.tabell.ForespoerselEntitet
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

class ForespoerselRepository(private val db: Database) {

    private val requestLatency = Summary.build()
        .name("simba_db_forespoersel_repo_latency_seconds")
        .help("database forespoerselRepo latency in seconds")
        .labelNames("method")
        .register()

    fun oppdaterOppgaveId(forespoerselId: String, oppgaveId: String) {
        val requestTimer = requestLatency.labels("oppdaterOppgaveId").startTimer()
        transaction(db) {
            ForespoerselEntitet.update(
                where = {
                    (ForespoerselEntitet.forespoerselId eq forespoerselId) and
                        ForespoerselEntitet.oppgaveId.isNull()
                }
            ) {
                it[ForespoerselEntitet.oppgaveId] = oppgaveId
            }
        }.also {
            requestTimer.observeDuration()
        }
    }

    fun oppdaterSakId(forespoerselId: String, sakId: String) {
        val requestTimer = requestLatency.labels("oppdaterSakId").startTimer()
        transaction(db) {
            ForespoerselEntitet.update(
                where = {
                    (ForespoerselEntitet.forespoerselId eq forespoerselId) and
                        ForespoerselEntitet.sakId.isNull()
                }
            ) {
                it[ForespoerselEntitet.sakId] = sakId
            }
        }.also {
            requestTimer.observeDuration()
        }
    }

    fun hentOppgaveId(forespoerselId: UUID): String? {
        val requestTimer = requestLatency.labels("hentOppgaveId").startTimer()
        return transaction(db) {
            ForespoerselEntitet
                .selectAll()
                .where { ForespoerselEntitet.forespoerselId eq forespoerselId.toString() }
                .firstOrNull(ForespoerselEntitet.oppgaveId)
        }.also {
            requestTimer.observeDuration()
        }
    }

    fun hentSakId(forespoerselId: UUID): String? {
        val requestTimer = requestLatency.labels("hentSakId").startTimer()
        return transaction(db) {
            ForespoerselEntitet
                .selectAll()
                .where { ForespoerselEntitet.forespoerselId eq forespoerselId.toString() }
                .firstOrNull(ForespoerselEntitet.sakId)
        }.also {
            requestTimer.observeDuration()
        }
    }

    fun lagreForespoersel(forespoerselId: String, organisasjonsnummer: String) {
        val requestTimer = requestLatency.labels("lagreForespoersel").startTimer()
        transaction(db) {
            ForespoerselEntitet.insert {
                it[this.forespoerselId] = forespoerselId
                it[orgnr] = organisasjonsnummer
                it[opprettet] = LocalDateTime.now()
            }
        }.also {
            requestTimer.observeDuration()
        }
    }
}
