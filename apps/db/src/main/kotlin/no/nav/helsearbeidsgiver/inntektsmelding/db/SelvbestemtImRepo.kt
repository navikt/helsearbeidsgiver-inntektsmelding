package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.hag.simba.utils.db.exposed.firstOrNull
import no.nav.helsearbeidsgiver.inntektsmelding.db.tabell.SelvbestemtInntektsmeldingEntitet
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDateTime
import java.util.UUID
import no.nav.hag.simba.utils.felles.domene.InntektsmeldingIntern as Inntektsmelding

class SelvbestemtImRepo(
    private val db: Database,
) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    fun hentNyesteIm(selvbestemtId: UUID): Inntektsmelding? =
        transaction(db) {
            SelvbestemtInntektsmeldingEntitet
                .selectAll()
                .where { SelvbestemtInntektsmeldingEntitet.selvbestemtId eq selvbestemtId }
                .orderBy(SelvbestemtInntektsmeldingEntitet.opprettet, SortOrder.DESC)
                .limit(1)
                .firstOrNull(SelvbestemtInntektsmeldingEntitet.inntektsmelding)
        }

    fun lagreIm(im: Inntektsmelding) {
        transaction(db) {
            SelvbestemtInntektsmeldingEntitet.insert {
                it[inntektsmeldingId] = im.id
                it[selvbestemtId] = im.type.id
                it[inntektsmelding] = im
            }
        }
    }

    fun oppdaterJournalpostId(
        inntektsmeldingId: UUID,
        journalpostId: String,
    ) {
        val antallOppdatert =
            transaction(db) {
                SelvbestemtInntektsmeldingEntitet.update(
                    where = {
                        (SelvbestemtInntektsmeldingEntitet.inntektsmeldingId eq inntektsmeldingId) and
                            SelvbestemtInntektsmeldingEntitet.journalpostId.isNull()
                    },
                ) {
                    it[this.journalpostId] = journalpostId
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
    }

    fun oppdaterSomProsessert(inntektsmeldingId: UUID) {
        val antallOppdatert =
            transaction(db) {
                SelvbestemtInntektsmeldingEntitet.update(
                    where = {
                        SelvbestemtInntektsmeldingEntitet.inntektsmeldingId eq inntektsmeldingId
                    },
                ) {
                    it[prosessert] = LocalDateTime.now()
                }
            }

        if (antallOppdatert != 1) {
            "Oppdaterte uventet antall ($antallOppdatert) rader under markering av inntektsmelding som prosessert.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }
    }
}
