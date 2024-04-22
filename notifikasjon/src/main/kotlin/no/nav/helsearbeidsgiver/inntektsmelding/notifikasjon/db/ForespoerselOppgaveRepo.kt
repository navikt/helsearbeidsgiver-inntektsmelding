package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db

import no.nav.helsearbeidsgiver.felles.db.exposed.firstOrNull
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.metrics.recordTime
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.sakLevetid
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.toJavaDuration

// TODO test
class ForespoerselOppgaveRepo(private val db: Database) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    fun hentOppgaveId(forespoerselId: UUID): String? {
        val oppgaveId = Metrics.dbForespoerselOppgave.recordTime(::hentOppgaveId) {
            transaction(db) {
                ForespoerselOppgave
                    .selectAll()
                    .where {
                        ForespoerselOppgave.forespoerselId eq forespoerselId
                    }
                    .firstOrNull(ForespoerselOppgave.oppgaveId)
            }
        }

        if (oppgaveId != null) {
            "Fant oppgave-ID '$oppgaveId' for forespurt inntektsmelding.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }
        } else {
            "Fant ikke oppgave-ID for forespurt inntektsmelding.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }

        return oppgaveId
    }

    fun lagreOppgaveId(forespoerselId: UUID, oppgaveId: String): Int {
        "Skal lagre oppgave-ID for forspurt inntektsmelding.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val antallLagret = Metrics.dbForespoerselOppgave.recordTime(::lagreOppgaveId) {
            transaction(db) {
                ForespoerselOppgave.insert {
                    it[this.forespoerselId] = forespoerselId
                    it[this.oppgaveId] = oppgaveId
                    it[slettes] = LocalDateTime.now().plus(sakLevetid.toJavaDuration())
                }
                    .insertedCount
            }
        }

        if (antallLagret == 1) {
            "Lagret oppgave-ID for forspurt inntektsmelding.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }
        } else {
            "Lagret uventet antall ($antallLagret) rader med oppgave-ID '$oppgaveId' for forespurt inntektsmelding.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }

        return antallLagret
    }

    fun lagreOppgaveFerdig(forespoerselId: UUID): Int {
        "Skal lagre ferdigstillig av oppgave for forespurt inntektsmelding.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val antallOppdatert = Metrics.dbForespoerselOppgave.recordTime(::lagreOppgaveFerdig) {
            transaction(db) {
                ForespoerselOppgave.update(
                    where = {
                        ForespoerselOppgave.forespoerselId eq forespoerselId
                    }
                ) {
                    it[ferdigstilt] = LocalDateTime.now()
                }
            }
        }

        if (antallOppdatert == 1) {
            "Lagret ferdigstillig av oppgave for forespurt inntektsmelding.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }
        } else {
            "Oppdaterte uventet antall ($antallOppdatert) rader med ferdigstilling av oppgave for forespurt inntektsmelding.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }

        return antallOppdatert
    }
}
