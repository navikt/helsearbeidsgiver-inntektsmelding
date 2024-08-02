package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.helsearbeidsgiver.felles.db.exposed.test.FunSpecWithDb
import no.nav.helsearbeidsgiver.felles.test.mock.mockEksternInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.db.tabell.ForespoerselEntitet
import no.nav.helsearbeidsgiver.inntektsmelding.db.tabell.InntektsmeldingEntitet
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

class TestRepo(
    private val db: Database,
) {
    fun hentRecordFraInntektsmelding(forespoerselId: UUID): ResultRow? =
        transaction(db) {
            InntektsmeldingEntitet
                .selectAll()
                .where {
                    InntektsmeldingEntitet.forespoerselId eq forespoerselId.toString()
                }.firstOrNull()
        }

    fun hentRecordFraForespoersel(forespoerselId: UUID): ResultRow? =
        transaction(db) {
            ForespoerselEntitet
                .selectAll()
                .where {
                    ForespoerselEntitet.forespoerselId eq forespoerselId.toString()
                }.firstOrNull()
        }
}

class RepositoryTest :
    FunSpecWithDb(listOf(InntektsmeldingEntitet, ForespoerselEntitet), { db ->

        val foresporselRepo = ForespoerselRepository(db)
        val inntektsmeldingRepo = InntektsmeldingRepository(db)
        val testRepo = TestRepo(db)
        val orgnr = "orgnr-456"

        test("skal lagre forespørsel") {
            transaction {
                InntektsmeldingEntitet.selectAll().toList()
            }.shouldBeEmpty()

            val forespoerselId = "abc-123"

            foresporselRepo.lagreForespoersel(forespoerselId, orgnr)

            shouldNotThrowAny {
                transaction {
                    ForespoerselEntitet
                        .selectAll()
                        .where {
                            (ForespoerselEntitet.forespoerselId eq forespoerselId) and
                                (ForespoerselEntitet.orgnr eq orgnr)
                        }.single()
                }
            }
        }

        test("skal lagre inntektsmelding med tilsvarende forespørsel") {
            transaction {
                InntektsmeldingEntitet.selectAll().toList()
            }.shouldBeEmpty()
            transaction {
                ForespoerselEntitet.selectAll().toList()
            }.shouldBeEmpty()

            val forespoerselId = UUID.randomUUID()
            val dok1 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = OffsetDateTime.now())

            foresporselRepo.lagreForespoersel(forespoerselId.toString(), orgnr)
            inntektsmeldingRepo.lagreInntektsmelding(forespoerselId, dok1)

            transaction {
                InntektsmeldingEntitet
                    .selectAll()
                    .where {
                        (InntektsmeldingEntitet.forespoerselId eq forespoerselId.toString()) and
                            (InntektsmeldingEntitet.dokument eq dok1)
                    }.single()
            }
            // lagre varianter:
            inntektsmeldingRepo.lagreInntektsmelding(forespoerselId, INNTEKTSMELDING_DOKUMENT_MED_TOM_FORESPURT_DATA)
            val im = inntektsmeldingRepo.hentNyesteInntektsmelding(forespoerselId)
            im shouldBe INNTEKTSMELDING_DOKUMENT_MED_TOM_FORESPURT_DATA

            inntektsmeldingRepo.lagreInntektsmelding(forespoerselId, INNTEKTSMELDING_DOKUMENT_MED_FORESPURT_DATA)
            val im2 = inntektsmeldingRepo.hentNyesteInntektsmelding(forespoerselId)
            im2?.forespurtData shouldNotBe (null)
            im2?.forespurtData shouldBe INNTEKTSMELDING_DOKUMENT_MED_FORESPURT_DATA.forespurtData

            inntektsmeldingRepo.lagreInntektsmelding(
                forespoerselId,
                INNTEKTSMELDING_DOKUMENT_MED_FORESPURT_DATA.copy(fullLønnIArbeidsgiverPerioden = null),
            )
            val im3 = inntektsmeldingRepo.hentNyesteInntektsmelding(forespoerselId)
            im3?.fullLønnIArbeidsgiverPerioden shouldBe null
        }

        test("skal returnere im med gammelt inntekt-format ok") {
            transaction {
                InntektsmeldingEntitet.selectAll().toList()
            }.shouldBeEmpty()
            transaction {
                ForespoerselEntitet.selectAll().toList()
            }.shouldBeEmpty()

            val forespoerselId = UUID.randomUUID()
            val dok1 = INNTEKTSMELDING_DOKUMENT_GAMMELT_INNTEKTFORMAT

            foresporselRepo.lagreForespoersel(forespoerselId.toString(), orgnr)
            inntektsmeldingRepo.lagreInntektsmelding(forespoerselId, dok1)

            transaction {
                InntektsmeldingEntitet
                    .selectAll()
                    .where {
                        (InntektsmeldingEntitet.forespoerselId eq forespoerselId.toString()) and
                            (InntektsmeldingEntitet.dokument eq dok1)
                    }.single()
            }
        }

        test("skal oppdatere journalpostId") {
            transaction {
                InntektsmeldingEntitet.selectAll().toList()
            }.shouldBeEmpty()

            val forespoerselId = UUID.randomUUID()
            val dok1 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = OffsetDateTime.now())
            val journalpost1 = "jp-1"

            foresporselRepo.lagreForespoersel(forespoerselId.toString(), orgnr)
            inntektsmeldingRepo.lagreInntektsmelding(forespoerselId, dok1)
            inntektsmeldingRepo.oppdaterJournalpostId(forespoerselId, journalpost1)
            val record = testRepo.hentRecordFraInntektsmelding(forespoerselId)
            record.shouldNotBeNull()
            val journalPostId = record.getOrNull(InntektsmeldingEntitet.journalpostId)
            journalPostId.shouldNotBeNull()
        }

        test("skal kun oppdatere siste inntektsmelding med journalpostId") {
            val forespoerselId = UUID.randomUUID()
            val journalpostId = "jp-mollefonken-kjele"

            foresporselRepo.lagreForespoersel(forespoerselId.toString(), orgnr)
            inntektsmeldingRepo.lagreInntektsmelding(forespoerselId, INNTEKTSMELDING_DOKUMENT)
            inntektsmeldingRepo.lagreInntektsmelding(forespoerselId, INNTEKTSMELDING_DOKUMENT)

            // Skal kun oppdatere siste
            inntektsmeldingRepo.oppdaterJournalpostId(forespoerselId, journalpostId)

            val resultat =
                transaction(db) {
                    InntektsmeldingEntitet
                        .selectAll()
                        .orderBy(InntektsmeldingEntitet.innsendt)
                        .toList()
                }

            resultat shouldHaveSize 2

            InntektsmeldingEntitet.apply {
                resultat[0][innsendt] shouldBeLessThan resultat[1][innsendt]

                resultat[0][dokument].shouldNotBeNull()
                resultat[0][this.journalpostId].shouldBeNull()

                resultat[1][dokument].shouldNotBeNull()
                resultat[1][this.journalpostId] shouldBe journalpostId
            }
        }

        test("skal _ikke_ oppdatere noen inntektsmeldinger med journalpostId dersom siste allerede har") {
            val forespoerselId = UUID.randomUUID()
            val gammelJournalpostId = "jp-traust-gevir"
            val nyJournalpostId = "jp-gallant-badehette"

            foresporselRepo.lagreForespoersel(forespoerselId.toString(), orgnr)
            inntektsmeldingRepo.lagreInntektsmelding(forespoerselId, INNTEKTSMELDING_DOKUMENT)
            inntektsmeldingRepo.lagreInntektsmelding(forespoerselId, INNTEKTSMELDING_DOKUMENT)
            inntektsmeldingRepo.oppdaterJournalpostId(forespoerselId, gammelJournalpostId)

            val resultatFoerNyJournalpostId =
                transaction(db) {
                    InntektsmeldingEntitet
                        .selectAll()
                        .orderBy(InntektsmeldingEntitet.innsendt)
                        .toList()
                }

            resultatFoerNyJournalpostId shouldHaveSize 2

            InntektsmeldingEntitet.apply {
                resultatFoerNyJournalpostId[0][innsendt] shouldBeLessThan resultatFoerNyJournalpostId[1][innsendt]

                resultatFoerNyJournalpostId[0][dokument].shouldNotBeNull()
                resultatFoerNyJournalpostId[0][journalpostId].shouldBeNull()

                resultatFoerNyJournalpostId[1][dokument].shouldNotBeNull()
                resultatFoerNyJournalpostId[1][journalpostId] shouldBe gammelJournalpostId
            }

            // Skal ha null effekt
            inntektsmeldingRepo.oppdaterJournalpostId(forespoerselId, nyJournalpostId)

            val resultsEtterNyJournalpostId =
                transaction(db) {
                    InntektsmeldingEntitet
                        .selectAll()
                        .orderBy(InntektsmeldingEntitet.innsendt)
                        .toList()
                }

            resultsEtterNyJournalpostId shouldHaveSize 2

            InntektsmeldingEntitet.apply {
                resultsEtterNyJournalpostId[0][innsendt] shouldBeLessThan resultsEtterNyJournalpostId[1][innsendt]

                resultsEtterNyJournalpostId[0][dokument].shouldNotBeNull()
                resultsEtterNyJournalpostId[0][journalpostId].shouldBeNull()

                resultsEtterNyJournalpostId[1][dokument].shouldNotBeNull()
                resultsEtterNyJournalpostId[1][journalpostId] shouldBe gammelJournalpostId
            }
        }

        test("skal _ikke_ oppdatere journalpostId for ekstern inntektsmelding") {
            val forespoerselId = UUID.randomUUID()
            val journalpostId = "jp-slem-fryser"

            foresporselRepo.lagreForespoersel(forespoerselId.toString(), orgnr)
            inntektsmeldingRepo.lagreInntektsmelding(forespoerselId, INNTEKTSMELDING_DOKUMENT)
            inntektsmeldingRepo.lagreEksternInntektsmelding(forespoerselId.toString(), mockEksternInntektsmelding())

            inntektsmeldingRepo.oppdaterJournalpostId(forespoerselId, journalpostId)

            val resultat =
                transaction(db) {
                    InntektsmeldingEntitet
                        .selectAll()
                        .orderBy(InntektsmeldingEntitet.innsendt)
                        .toList()
                }

            resultat shouldHaveSize 2

            InntektsmeldingEntitet.apply {
                resultat[0][innsendt] shouldBeLessThan resultat[1][innsendt]

                resultat[0][dokument].shouldNotBeNull()
                resultat[0][this.journalpostId] shouldBe journalpostId

                resultat[1][eksternInntektsmelding].shouldNotBeNull()
                resultat[1][this.journalpostId].shouldBeNull()
            }
        }

        test("skal oppdatere sakId") {
            transaction {
                InntektsmeldingEntitet.selectAll().toList()
            }.shouldBeEmpty()

            val forespoerselId = UUID.randomUUID()
            val sakId1 = "sak1-1"

            foresporselRepo.lagreForespoersel(forespoerselId.toString(), orgnr)
            foresporselRepo.oppdaterSakId(forespoerselId.toString(), sakId1)
            val record = testRepo.hentRecordFraForespoersel(forespoerselId)
            record.shouldNotBeNull()
            val sakId = record.getOrNull(ForespoerselEntitet.sakId)
            sakId.shouldNotBeNull()
            sakId.shouldBeEqualComparingTo(sakId1)
        }

        test("skal oppdatere oppgaveId") {
            transaction {
                InntektsmeldingEntitet.selectAll().toList()
            }.shouldBeEmpty()

            val forespoerselId = UUID.randomUUID()
            val oppgaveId1 = "oppg-1"

            foresporselRepo.lagreForespoersel(forespoerselId.toString(), orgnr)
            foresporselRepo.oppdaterOppgaveId(forespoerselId.toString(), oppgaveId1)
            val rad = testRepo.hentRecordFraForespoersel(forespoerselId)
            rad.shouldNotBeNull()
            val oppgaveId = rad.getOrNull(ForespoerselEntitet.oppgaveId)
            oppgaveId.shouldNotBeNull()
            oppgaveId.shouldBeEqualComparingTo(oppgaveId1)
        }
    })
