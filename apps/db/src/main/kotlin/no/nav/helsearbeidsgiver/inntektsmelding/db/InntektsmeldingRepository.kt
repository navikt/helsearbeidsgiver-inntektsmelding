package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.LagretInntektsmelding
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.utils.konverterEndringAarsakTilListe
import no.nav.helsearbeidsgiver.inntektsmelding.db.domene.InntektsmeldingGammeltFormat
import no.nav.helsearbeidsgiver.inntektsmelding.db.domene.convert
import no.nav.helsearbeidsgiver.inntektsmelding.db.domene.convertAgp
import no.nav.helsearbeidsgiver.inntektsmelding.db.domene.convertInntekt
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
                        InntektsmeldingEntitet.skjema,
                        InntektsmeldingEntitet.dokument,
                        InntektsmeldingEntitet.eksternInntektsmelding,
                        InntektsmeldingEntitet.innsendt,
                        InntektsmeldingEntitet.avsenderNavn,
                    ).where { InntektsmeldingEntitet.forespoerselId eq forespoerselId.toString() }
                    .orderBy(InntektsmeldingEntitet.innsendt, SortOrder.DESC)
                    .limit(1)
                    .map {
                        InntektsmeldingResult(
                            it[InntektsmeldingEntitet.skjema],
                            it[InntektsmeldingEntitet.dokument],
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
                                avsenderNavn = result.avsenderNavn ?: result.inntektsmeldingGammeltFormat?.innsenderNavn,
                                skjema = result.skjema.konverterEndringAarsakTilListe(),
                                mottatt = result.mottatt,
                            )

                        result.inntektsmeldingGammeltFormat != null -> {
                            val bakoverkompatibeltSkjema =
                                SkjemaInntektsmelding(
                                    forespoerselId = forespoerselId,
                                    avsenderTlf = result.inntektsmeldingGammeltFormat.telefonnummer.orEmpty(),
                                    agp = result.inntektsmeldingGammeltFormat.convertAgp(),
                                    inntekt = result.inntektsmeldingGammeltFormat.convertInntekt(),
                                    refusjon = result.inntektsmeldingGammeltFormat.refusjon.convert(),
                                )

                            LagretInntektsmelding.Skjema(
                                avsenderNavn = result.inntektsmeldingGammeltFormat.innsenderNavn,
                                skjema = bakoverkompatibeltSkjema,
                                mottatt = result.mottatt,
                            )
                        }

                        result.eksternInntektsmelding != null -> LagretInntektsmelding.Ekstern(result.eksternInntektsmelding)
                        else -> null
                    }
                }
        }

    fun oppdaterJournalpostId(
        inntektsmeldingId: UUID,
        journalpostId: String,
    ) {
        Metrics.dbInntektsmelding.recordTime(InntektsmeldingRepository::oppdaterJournalpostId) {
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

    fun lagreInntektsmeldingSkjema(
        inntektsmeldingId: UUID,
        inntektsmeldingSkjema: SkjemaInntektsmelding,
        mottatt: LocalDateTime,
    ) {
        transaction(db) {
            InntektsmeldingEntitet.insert {
                it[this.inntektsmeldingId] = inntektsmeldingId
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

    fun hentNyesteBerikedeInntektsmeldingId(forespoerselId: UUID): UUID? =
        transaction(db) {
            hentNyesteImQuery(forespoerselId)
                .firstOrNull()
                ?.getOrNull(InntektsmeldingEntitet.inntektsmeldingId)
        }

    fun oppdaterMedBeriketDokument(inntektsmelding: Inntektsmelding) {
        val antallOppdatert =
            transaction(db) {
                InntektsmeldingEntitet.update(
                    where = {
                        InntektsmeldingEntitet.inntektsmeldingId eq inntektsmelding.id
                    },
                ) {
                    it[dokument] = inntektsmelding.convert()
                    it[avsenderNavn] = inntektsmelding.avsender.navn
                }
            }

        if (antallOppdatert == 1) {
            "Lagret inntektsmelding for forespørsel-ID ${inntektsmelding.type.id} i database.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }
        } else {
            "Oppdaterte uventet antall ($antallOppdatert) rader ved lagring av inntektsmelding med forespørsel-ID ${inntektsmelding.type.id}.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }
    }
}

private class InntektsmeldingResult(
    val skjema: SkjemaInntektsmelding?,
    val inntektsmeldingGammeltFormat: InntektsmeldingGammeltFormat?,
    val eksternInntektsmelding: EksternInntektsmelding?,
    val mottatt: LocalDateTime,
    val avsenderNavn: String?,
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
