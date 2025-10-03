package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.hag.simba.kontrakt.domene.inntektsmelding.EksternInntektsmelding
import no.nav.hag.simba.kontrakt.domene.inntektsmelding.LagretInntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.db.tabell.InntektsmeldingEntitet
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import org.jetbrains.exposed.sql.Database
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

    fun hentNyesteInntektsmelding(forespoerselId: UUID): LagretInntektsmelding? =
        transaction(db) {
            InntektsmeldingEntitet
                .select(
                    InntektsmeldingEntitet.skjema,
                    InntektsmeldingEntitet.eksternInntektsmelding,
                    InntektsmeldingEntitet.innsendt,
                    InntektsmeldingEntitet.avsenderNavn,
                ).where { InntektsmeldingEntitet.forespoerselId eq forespoerselId }
                .orderBy(InntektsmeldingEntitet.innsendt, SortOrder.DESC)
                .limit(1)
                .map {
                    InntektsmeldingResult(
                        it[InntektsmeldingEntitet.skjema],
                        it[InntektsmeldingEntitet.eksternInntektsmelding],
                        it[InntektsmeldingEntitet.innsendt],
                        it[InntektsmeldingEntitet.avsenderNavn],
                    )
                }
        }.firstOrNull()
            ?.let { result ->
                when {
                    result.skjema != null ->
                        LagretInntektsmelding.Skjema(
                            avsenderNavn = result.avsenderNavn,
                            skjema = result.skjema,
                            mottatt = result.mottatt,
                        )

                    result.eksternInntektsmelding != null -> LagretInntektsmelding.Ekstern(result.eksternInntektsmelding)
                    else -> null
                }
            }

    fun oppdaterJournalpostId(
        inntektsmeldingId: UUID,
        journalpostId: String,
    ) {
        val antallOppdatert =
            transaction(db) {
                InntektsmeldingEntitet.update(
                    where = { InntektsmeldingEntitet.inntektsmeldingId eq inntektsmeldingId },
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
    }

    fun lagreEksternInntektsmelding(
        forespoerselId: UUID,
        eksternIm: EksternInntektsmelding,
    ) {
        transaction(db) {
            InntektsmeldingEntitet.insert {
                it[this.forespoerselId] = forespoerselId
                it[eksternInntektsmelding] = eksternIm
                it[innsendt] = eksternIm.tidspunkt
            }
        }
    }

    fun lagreInntektsmeldingSkjema(
        inntektsmeldingId: UUID,
        inntektsmeldingSkjema: SkjemaInntektsmelding,
        avsenderFnr: Fnr,
        mottatt: LocalDateTime,
    ) {
        transaction(db) {
            InntektsmeldingEntitet.insert {
                it[this.inntektsmeldingId] = inntektsmeldingId
                it[forespoerselId] = inntektsmeldingSkjema.forespoerselId
                it[skjema] = inntektsmeldingSkjema
                it[this.avsenderFnr] = avsenderFnr.verdi
                it[innsendt] = mottatt
            }
        }
    }

    fun hentNyesteInntektsmeldingSkjema(forespoerselId: UUID): SkjemaInntektsmelding? =
        transaction(db) {
            InntektsmeldingEntitet
                .selectAll()
                .where { InntektsmeldingEntitet.forespoerselId eq forespoerselId }
                .orderBy(InntektsmeldingEntitet.innsendt, SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.getOrNull(InntektsmeldingEntitet.skjema)
        }

    fun hentNyesteInntektsmeldingId(forespoerselId: UUID): UUID? =
        transaction(db) {
            InntektsmeldingEntitet
                .selectAll()
                .where { (InntektsmeldingEntitet.forespoerselId eq forespoerselId) and InntektsmeldingEntitet.inntektsmeldingId.isNotNull() }
                .orderBy(InntektsmeldingEntitet.innsendt, SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.getOrNull(InntektsmeldingEntitet.inntektsmeldingId)
        }

    fun oppdaterMedInntektsmelding(inntektsmelding: Inntektsmelding) {
        val antallOppdatert =
            transaction(db) {
                InntektsmeldingEntitet.update(
                    where = {
                        InntektsmeldingEntitet.inntektsmeldingId eq inntektsmelding.id
                    },
                ) {
                    it[this.inntektsmelding] = inntektsmelding
                    it[avsenderNavn] = inntektsmelding.avsender.navn
                }
            }

        if (antallOppdatert == 1) {
            "Lagret inntektsmelding.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }
        } else {
            "Oppdaterte uventet antall ($antallOppdatert) rader ved lagring av inntektsmelding.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }
    }

    fun oppdaterSomProsessert(inntektsmeldingId: UUID) {
        val antallOppdatert =
            transaction(db) {
                InntektsmeldingEntitet.update(
                    where = {
                        InntektsmeldingEntitet.inntektsmeldingId eq inntektsmeldingId
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

private class InntektsmeldingResult(
    val skjema: SkjemaInntektsmelding?,
    val eksternInntektsmelding: EksternInntektsmelding?,
    val mottatt: LocalDateTime,
    val avsenderNavn: String?,
)
