package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.domene.inntektsmelding.Utils.convert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Utils.convertAgp
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Utils.convertInntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.LagretInntektsmelding
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.utils.konverterEndringAarsakTilListe
import no.nav.helsearbeidsgiver.inntektsmelding.db.tabell.InntektsmeldingEntitet
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
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

    fun hentNyesteInntektsmelding(forespoerselId: UUID): LagretInntektsmelding? =
        Metrics.dbInntektsmelding.recordTime(InntektsmeldingRepository::hentNyesteInntektsmelding) {
            transaction(db) {
                InntektsmeldingEntitet
                    .select(
                        InntektsmeldingEntitet.dokument,
                        InntektsmeldingEntitet.skjema,
                        InntektsmeldingEntitet.eksternInntektsmelding,
                        InntektsmeldingEntitet.innsendt,
                    ).where { InntektsmeldingEntitet.forespoerselId eq forespoerselId.toString() }
                    .orderBy(InntektsmeldingEntitet.innsendt, SortOrder.DESC)
                    .limit(1)
                    .map {
                        Quadruple(
                            it[InntektsmeldingEntitet.dokument],
                            it[InntektsmeldingEntitet.skjema],
                            it[InntektsmeldingEntitet.eksternInntektsmelding],
                            it[InntektsmeldingEntitet.innsendt],
                        )
                    }
            }.firstOrNull()
                ?.let { result ->
                    val inntektsmelding = result.first
                    val skjema = result.second
                    val eksternInntektsmelding = result.third
                    val mottatt = result.fourth

                    when {
                        skjema != null -> LagretInntektsmelding.Skjema(inntektsmelding?.innsenderNavn, skjema.konverterEndringAarsakTilListe(), mottatt)
                        inntektsmelding != null -> {
                            val bakoverkompatibeltSkjema =
                                SkjemaInntektsmelding(
                                    forespoerselId = forespoerselId,
                                    avsenderTlf = inntektsmelding.telefonnummer.orEmpty(),
                                    agp = inntektsmelding.convertAgp(),
                                    inntekt = inntektsmelding.convertInntekt(),
                                    refusjon = inntektsmelding.refusjon.convert(),
                                )

                            LagretInntektsmelding.Skjema(inntektsmelding.innsenderNavn, bakoverkompatibeltSkjema, mottatt)
                        }
                        eksternInntektsmelding != null -> LagretInntektsmelding.Ekstern(eksternInntektsmelding)
                        else -> null
                    }
                }
        }

    fun oppdaterJournalpostId(
        innsendingId: Long,
        journalpostId: String,
    ) {
        Metrics.dbInntektsmelding.recordTime(InntektsmeldingRepository::oppdaterJournalpostId) {
            val antallOppdatert =
                transaction(db) {
                    InntektsmeldingEntitet.update(
                        where = { InntektsmeldingEntitet.id eq innsendingId },
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
    }

    fun lagreEksternInntektsmelding(
        forespoerselId: UUID,
        eksternIm: EksternInntektsmelding,
    ) {
        transaction(db) {
            InntektsmeldingEntitet.insert {
                it[this.forespoerselId] = forespoerselId.toString()
                it[eksternInntektsmelding] = eksternIm
                it[innsendt] = eksternIm.tidspunkt
            }
        }
    }

    fun hentNyesteBerikedeInnsendingId(forespoerselId: UUID): Long? =
        Metrics.dbInntektsmelding.recordTime(InntektsmeldingRepository::hentNyesteBerikedeInnsendingId) {
            transaction(db) {
                hentNyesteImQuery(forespoerselId)
                    .firstOrNull()
                    ?.getOrNull(InntektsmeldingEntitet.id)
            }
        }

    fun lagreInntektsmeldingSkjema(
        inntektsmeldingSkjema: SkjemaInntektsmelding,
        mottatt: LocalDateTime,
    ): Long =
        Metrics.dbInntektsmelding.recordTime(InntektsmeldingRepository::lagreInntektsmeldingSkjema) {
            transaction(db) {
                InntektsmeldingEntitet.insert {
                    it[this.forespoerselId] = inntektsmeldingSkjema.forespoerselId.toString()
                    it[skjema] = inntektsmeldingSkjema
                    it[innsendt] = mottatt
                } get InntektsmeldingEntitet.id
            }
        }

    fun hentNyesteInntektsmeldingSkjema(forespoerselId: UUID): SkjemaInntektsmelding? =
        Metrics.dbInntektsmelding.recordTime(InntektsmeldingRepository::hentNyesteInntektsmeldingSkjema) {
            transaction(db) {
                hentNyesteImSkjemaQuery(forespoerselId)
                    .firstOrNull()
                    ?.getOrNull(InntektsmeldingEntitet.skjema)
            }
        }

    fun oppdaterMedBeriketDokument(
        forespoerselId: UUID,
        innsendingId: Long, // TODO: denne kan erstattes med inntektsmelding.id når ny IM payload brukes
        inntektsmeldingDokument: Inntektsmelding,
    ) {
        val antallOppdatert =
            Metrics.dbInntektsmelding.recordTime(InntektsmeldingRepository::oppdaterMedBeriketDokument) {
                transaction(db) {
                    InntektsmeldingEntitet.update(
                        where = {
                            InntektsmeldingEntitet.id eq innsendingId
                        },
                    ) {
                        it[dokument] = inntektsmeldingDokument
                    }
                }
            }

        if (antallOppdatert == 1) {
            "Lagret inntektsmelding for forespørsel-ID $forespoerselId i database.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }
        } else {
            "Oppdaterte uventet antall ($antallOppdatert) rader ved lagring av inntektsmelding med forespørsel-ID $forespoerselId.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }
    }
}

private class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

private fun hentNyesteImQuery(forespoerselId: UUID): Query =
    InntektsmeldingEntitet
        .selectAll()
        .where { (InntektsmeldingEntitet.forespoerselId eq forespoerselId.toString()) and InntektsmeldingEntitet.dokument.isNotNull() }
        .orderBy(InntektsmeldingEntitet.innsendt, SortOrder.DESC)
        .limit(1)

private fun hentNyesteImSkjemaQuery(forespoerselId: UUID): Query =
    InntektsmeldingEntitet
        .selectAll()
        .where { InntektsmeldingEntitet.forespoerselId eq forespoerselId.toString() }
        .orderBy(InntektsmeldingEntitet.innsendt, SortOrder.DESC)
        .limit(1)
