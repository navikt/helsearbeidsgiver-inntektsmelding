package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db

import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.sakLevetid
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.toJavaDuration

class SelvbestemtSakRepo(
    private val db: Database,
) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    fun lagreSakId(
        selvbestemtId: UUID,
        sakId: String,
    ) {
        "Skal lagre sak-ID for selvbestemt inntektsmelding.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        runCatching {
            Metrics.dbSelvbestemtSak.recordTime(::lagreSakId) {
                transaction(db) {
                    SelvbestemtSak
                        .insert {
                            it[this.selvbestemtId] = selvbestemtId
                            it[this.sakId] = sakId
                            it[slettes] = LocalDateTime.now().plus(sakLevetid.toJavaDuration())
                        }
                }
            }
        }.onSuccess {
            "Lagret sak-ID for selvbestemt inntektsmelding.".also { msg ->
                logger.info(msg)
                sikkerLogger.info(msg)
            }
        }.onFailure { error ->
            "Lagret _ikke_ sak-ID '$sakId' for selvbestemt inntektsmelding.".also { msg ->
                logger.error(msg)
                sikkerLogger.error(msg, error)
            }
        }
    }
}
