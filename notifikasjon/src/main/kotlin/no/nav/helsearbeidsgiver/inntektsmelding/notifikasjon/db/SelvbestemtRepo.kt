package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db

import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.metrics.recordTime
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.sakLevetid
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.toJavaDuration

class SelvbestemtRepo(private val db: Database) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    fun lagreSakId(
        selvbestemtId: UUID,
        sakId: String,
    ): Int {
        "Skal lagre sak-ID for selvbestemt inntektsmelding.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        return Metrics.dbSelvbestemtSak.recordTime(::lagreSakId) {
            transaction(db) {
                SelvbestemtSak.insert {
                    it[this.selvbestemtId] = selvbestemtId
                    it[this.sakId] = sakId
                    it[slettes] = LocalDateTime.now().plus(sakLevetid.toJavaDuration())
                }
                    .insertedCount
            }
        }
            .also {
                "Lagret sak-ID for selvbestemt inntektsmelding.".also {
                    logger.info(it)
                    sikkerLogger.info(it)
                }
            }
    }
}
