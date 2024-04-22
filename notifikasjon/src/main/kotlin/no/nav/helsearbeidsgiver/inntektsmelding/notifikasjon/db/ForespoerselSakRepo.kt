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
class ForespoerselSakRepo(private val db: Database) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    fun hentSakId(forespoerselId: UUID): String? {
        val sakId = Metrics.dbForespoerselSak.recordTime(::hentSakId) {
            transaction(db) {
                ForespoerselSak
                    .selectAll()
                    .where {
                        ForespoerselSak.forespoerselId eq forespoerselId
                    }
                    .firstOrNull(ForespoerselSak.sakId)
            }
        }

        if (sakId != null) {
            "Fant sak-ID '$sakId' for forespurt inntektsmelding.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }
        } else {
            "Fant ikke sak-ID for forespurt inntektsmelding.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }

        return sakId
    }

    fun lagreSakId(forespoerselId: UUID, sakId: String): Int {
        "Skal lagre sak-ID for forespurt inntektsmelding.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val antallLagret = Metrics.dbForespoerselSak.recordTime(::lagreSakId) {
            transaction(db) {
                ForespoerselSak.insert {
                    it[this.forespoerselId] = forespoerselId
                    it[this.sakId] = sakId
                    it[slettes] = LocalDateTime.now().plus(sakLevetid.toJavaDuration())
                }
                    .insertedCount
            }
        }

        if (antallLagret == 1) {
            "Lagret sak-ID for forespurt inntektsmelding.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }
        } else {
            "Lagret uventet antall ($antallLagret) rader med sak-ID '$sakId' for forespurt inntektsmelding.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }

        return antallLagret
    }

    fun lagreSakFerdig(forespoerselId: UUID): Int {
        "Skal lagre ferdigstillig av sak for forespurt inntektsmelding.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val antallOppdatert = Metrics.dbForespoerselSak.recordTime(::lagreSakFerdig) {
            transaction(db) {
                ForespoerselSak.update(
                    where = {
                        ForespoerselSak.forespoerselId eq forespoerselId
                    }
                ) {
                    it[ferdigstilt] = LocalDateTime.now()
                }
            }
        }

        if (antallOppdatert == 1) {
            "Lagret ferdigstillig av sak for forespurt inntektsmelding.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }
        } else {
            "Oppdaterte uventet antall ($antallOppdatert) rader med ferdigstilling av sak for forespurt inntektsmelding.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }

        return antallOppdatert
    }
}
