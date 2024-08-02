package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.prometheus.client.Summary
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.db.tabell.InntektsmeldingEntitet
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

class InntektsmeldingRepository(
    private val db: Database,
) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    private val requestLatency =
        Summary
            .build()
            .name("simba_db_inntektsmelding_repo_latency_seconds")
            .help("database inntektsmeldingRepo latency in seconds")
            .labelNames("method")
            .register()

    fun lagreInntektsmelding(
        forespoerselId: UUID,
        inntektsmeldingDokument: Inntektsmelding,
    ) {
        val requestTimer = requestLatency.labels("lagreInntektsmelding").startTimer()
        transaction(db) {
            InntektsmeldingEntitet.insert {
                it[this.forespoerselId] = forespoerselId.toString()
                it[dokument] = inntektsmeldingDokument
                it[innsendt] = LocalDateTime.now()
            }
        }.also {
            requestTimer.observeDuration()
        }
    }

    fun hentNyeste(forespoerselId: UUID): Inntektsmelding? {
        val requestTimer = requestLatency.labels("hentNyeste").startTimer()
        return transaction(db) {
            hentNyesteImQuery(forespoerselId)
                .firstOrNull()
                ?.getOrNull(InntektsmeldingEntitet.dokument)
        }.also {
            requestTimer.observeDuration()
        }
    }

    fun hentNyesteEksternEllerInternInntektsmelding(forespoerselId: UUID): Pair<Inntektsmelding?, EksternInntektsmelding?> {
        val requestTimer = requestLatency.labels("hentNyesteInternEllerEkstern").startTimer()
        return transaction(db) {
            InntektsmeldingEntitet
                .select(InntektsmeldingEntitet.dokument, InntektsmeldingEntitet.eksternInntektsmelding)
                .where { InntektsmeldingEntitet.forespoerselId eq forespoerselId.toString() }
                .orderBy(InntektsmeldingEntitet.innsendt, SortOrder.DESC)
                .limit(1)
                .map {
                    Pair(
                        it[InntektsmeldingEntitet.dokument],
                        it[InntektsmeldingEntitet.eksternInntektsmelding],
                    )
                }.firstOrNull()
        }.orDefault(Pair(null, null))
            .also {
                requestTimer.observeDuration()
            }
    }

    fun oppdaterJournalpostId(
        forespoerselId: UUID,
        journalpostId: String,
    ) {
        val requestTimer = requestLatency.labels("oppdaterJournalpostId").startTimer()

        val antallOppdatert =
            transaction(db) {
                InntektsmeldingEntitet.update(
                    where = {
                        val nyesteImIdQuery = hentNyesteImQuery(forespoerselId).adjustSelect { select(InntektsmeldingEntitet.id) }

                        (InntektsmeldingEntitet.id eqSubQuery nyesteImIdQuery) and
                            InntektsmeldingEntitet.journalpostId.isNull()
                    },
                ) {
                    it[InntektsmeldingEntitet.journalpostId] = journalpostId
                }
            }

        if (antallOppdatert == 1) {
            "Lagret journalpost-ID '$journalpostId' i database.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }
        } else {
            "Oppdaterte uventet antall ($antallOppdatert) rader med journalpost-ID '$journalpostId'.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }

        requestTimer.observeDuration()
    }

    fun lagreEksternInntektsmelding(
        forespoerselId: String,
        eksternIm: EksternInntektsmelding,
    ) {
        transaction(db) {
            InntektsmeldingEntitet.insert {
                it[this.forespoerselId] = forespoerselId
                it[eksternInntektsmelding] = eksternIm
                it[innsendt] = LocalDateTime.now()
            }
        }
    }

    private fun hentNyesteImQuery(forespoerselId: UUID): Query =
        InntektsmeldingEntitet
            .selectAll()
            .where { (InntektsmeldingEntitet.forespoerselId eq forespoerselId.toString()) and InntektsmeldingEntitet.dokument.isNotNull() }
            .orderBy(InntektsmeldingEntitet.innsendt, SortOrder.DESC)
            .limit(1)
}
