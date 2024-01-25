package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.prometheus.client.Summary
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.firstOrNull
import no.nav.helsearbeidsgiver.inntektsmelding.db.tabell.ForespoerselEntitet
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
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
            ForespoerselEntitet.update({ (ForespoerselEntitet.forespoerselId eq forespoerselId) and (ForespoerselEntitet.oppgaveId eq null) }) {
                it[ForespoerselEntitet.oppgaveId] = oppgaveId
            }
        }.also {
            requestTimer.observeDuration()
        }
    }

    fun oppdaterSakId(forespoerselId: String, sakId: String) {
        val requestTimer = requestLatency.labels("oppdaterSakId").startTimer()
        transaction(db) {
            ForespoerselEntitet.update({ (ForespoerselEntitet.forespoerselId eq forespoerselId) and (ForespoerselEntitet.sakId eq null) }) {
                it[ForespoerselEntitet.sakId] = sakId
            }
        }.also {
            requestTimer.observeDuration()
        }
    }

    fun hentOppgaveId(forespoerselId: UUID): String? {
        val requestTimer = requestLatency.labels("hentOppgaveId").startTimer()
        return transaction(db) {
            ForespoerselEntitet.select {
                ForespoerselEntitet.forespoerselId eq forespoerselId.toString()
            }
                .firstOrNull(ForespoerselEntitet.oppgaveId)
        }.also {
            requestTimer.observeDuration()
        }
    }

    fun hentSakId(forespoerselId: UUID): String? {
        val requestTimer = requestLatency.labels("hentSakId").startTimer()
        return transaction(db) {
            ForespoerselEntitet.select {
                ForespoerselEntitet.forespoerselId eq forespoerselId.toString()
            }
                .firstOrNull(ForespoerselEntitet.sakId)
        }.also {
            requestTimer.observeDuration()
        }
    }

    fun hentOrgnr(forespoerselId: UUID): String? {
        val requestTimer = requestLatency.labels("hentOrgnr").startTimer()
        return transaction(db) {
            ForespoerselEntitet.let {
                it.select { (it.forespoerselId eq forespoerselId.toString()) }
                    .firstOrNull(it.orgnr)
            }
        }.also {
            requestTimer.observeDuration()
        }
    }

    fun lagreForespoersel(forespoerselId: String, organisasjonsnummer: String) {
        val requestTimer = requestLatency.labels("lagreForespoersel").startTimer()
        transaction(db) {
            ForespoerselEntitet.run {
                insert {
                    it[this.forespoerselId] = forespoerselId
                    it[orgnr] = organisasjonsnummer
                    it[opprettet] = LocalDateTime.now()
                }
            }
        }.also {
            requestTimer.observeDuration()
        }
    }
}
