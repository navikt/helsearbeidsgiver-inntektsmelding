package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.prometheus.client.Summary
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.db.tabell.InntektsmeldingEntitet
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

class InntektsmeldingRepository(private val db: Database) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    private val requestLatency = Summary.build()
        .name("simba_db_inntektsmelding_repo_latency_seconds")
        .help("database inntektsmeldingRepo latency in seconds")
        .labelNames("method")
        .register()

    fun lagreInntektsmelding(forespørselId: String, inntektsmeldingDokument: Inntektsmelding) {
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

    fun hentNyeste(forespoerselId: UUID): Inntektsmelding? {
        val requestTimer = requestLatency.labels("hentNyeste").startTimer()
        return transaction(db) {
            hentNyesteQuery(forespoerselId)
                .firstOrNull()
                ?.getOrNull(InntektsmeldingEntitet.dokument)
        }.also {
            requestTimer.observeDuration()
        }
    }

    fun hentNyesteEksternEllerInternInntektsmelding(forespørselId: String): Pair<Inntektsmelding?, EksternInntektsmelding?>? {
        val requestTimer = requestLatency.labels("hentNyesteInternEllerEkstern").startTimer()
        return transaction(db) {
            InntektsmeldingEntitet.slice(InntektsmeldingEntitet.dokument, InntektsmeldingEntitet.eksternInntektsmelding)
                .select { (InntektsmeldingEntitet.forespoerselId eq forespørselId) }
                .orderBy(InntektsmeldingEntitet.innsendt, SortOrder.DESC)
                .limit(1)
                .map {
                    Pair(
                        it[InntektsmeldingEntitet.dokument],
                        it[InntektsmeldingEntitet.eksternInntektsmelding]
                    )
                }
                .firstOrNull()
                .also {
                    requestTimer.observeDuration()
                }
        }
    }

    fun oppdaterJournalpostId(forespoerselId: UUID, journalpostId: String) {
        val requestTimer = requestLatency.labels("oppdaterJournalpostId").startTimer()

        val antallOppdatert = transaction(db) {
            InntektsmeldingEntitet.update(
                where = {
                    (InntektsmeldingEntitet.id eqSubQuery hentNyesteQuery(forespoerselId).adjustSlice { slice(InntektsmeldingEntitet.id) }) and
                        InntektsmeldingEntitet.journalpostId.isNull()
                }
            ) {
                it[InntektsmeldingEntitet.journalpostId] = journalpostId
            }
        }

        if (antallOppdatert != 1) {
            logger.error("Oppdaterte uventet antall ($antallOppdatert) rader med journalpostId.")
            sikkerLogger.error("Oppdaterte uventet antall ($antallOppdatert) rader med journalpostId.")
        }

        requestTimer.observeDuration()
    }

    fun lagreEksternInntektsmelding(forespørselId: String, eksternIm: EksternInntektsmelding) {
        transaction(db) {
            InntektsmeldingEntitet.run {
                insert {
                    it[forespoerselId] = forespørselId
                    it[eksternInntektsmelding] = eksternIm
                    it[innsendt] = LocalDateTime.now()
                }
            }
        }
    }

    private fun hentNyesteQuery(forespoerselId: UUID): Query =
        InntektsmeldingEntitet.select {
            (InntektsmeldingEntitet.forespoerselId eq forespoerselId.toString()) and InntektsmeldingEntitet.dokument.isNotNull()
        }
            .orderBy(InntektsmeldingEntitet.innsendt, SortOrder.DESC)
            .limit(1)
}
