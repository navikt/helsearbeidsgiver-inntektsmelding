package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.prometheus.client.Summary
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.InntektsmeldingEntitet
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

class InntektsmeldingRepository(private val db: Database) {

    private val requestLatency = Summary.build()
        .name("simba_db_inntektsmelding_repo_latency_seconds")
        .help("database inntektsmeldingRepo latency in seconds")
        .labelNames("method")
        .register()

    fun lagreInntektsmelding(forespørselId: String, inntektsmeldingDokument: InntektsmeldingDokument) {
        val requestTimer = requestLatency.labels("lagreInntektsmelding").startTimer()
        transaction(db) {
            InntektsmeldingEntitet.run {
                insert {
                    it[forespoerselId] = forespørselId
                    it[dokument] = inntektsmeldingDokument
                    it[innsendt] = LocalDateTime.now()
                }
            }
        }.also {
            requestTimer.observeDuration()
        }
    }
    fun hentNyeste(forespørselId: String): InntektsmeldingDokument? {
        val requestTimer = requestLatency.labels("hentNyeste").startTimer()
        return transaction(db) {
            InntektsmeldingEntitet.run {
                select { (forespoerselId eq forespørselId) }.orderBy(innsendt, SortOrder.DESC)
            }.firstOrNull()?.getOrNull(InntektsmeldingEntitet.dokument)
        }.also {
            requestTimer.observeDuration()
        }
    }

    fun oppdaterJournalpostId(journalpostId: String, forespørselId: String) {
        val requestTimer = requestLatency.labels("oppdaterJournalpostId").startTimer()
        transaction(db) {
            InntektsmeldingEntitet.update({ (InntektsmeldingEntitet.forespoerselId eq forespørselId) and (InntektsmeldingEntitet.journalpostId eq null) }) {
                it[InntektsmeldingEntitet.journalpostId] = journalpostId
            }
        }.also {
            requestTimer.observeDuration()
        }
    }
}
