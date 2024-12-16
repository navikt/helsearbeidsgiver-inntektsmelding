package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.db.exposed.test.FunSpecWithDb
import no.nav.helsearbeidsgiver.felles.test.mock.mockEksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingGammeltFormat
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.mock.randomDigitString
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
}

class InntektsmeldingRepositoryTest :
    FunSpecWithDb(listOf(InntektsmeldingEntitet), { db ->

        val inntektsmeldingRepo = InntektsmeldingRepository(db)
        val testRepo = TestRepo(db)

        test("skal lagre inntektsmeldingskjema og dokument") {
            transaction {
                InntektsmeldingEntitet.selectAll().toList()
            }.shouldBeEmpty()

            val skjema = mockSkjemaInntektsmelding()

            val innsendingId = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema)

            val beriketDokument = mockInntektsmeldingGammeltFormat().copy(tidspunkt = OffsetDateTime.now())
            inntektsmeldingRepo.oppdaterMedBeriketDokument(skjema.forespoerselId, innsendingId, beriketDokument)

            transaction {
                val inntektsmeldinger =
                    InntektsmeldingEntitet
                        .selectAll()
                        .where {
                            (InntektsmeldingEntitet.forespoerselId eq skjema.forespoerselId.toString())
                        }.toList()

                inntektsmeldinger.size shouldBe 1

                inntektsmeldinger.first().getOrNull(InntektsmeldingEntitet.journalpostId) shouldBe null
                inntektsmeldinger.first().getOrNull(InntektsmeldingEntitet.eksternInntektsmelding) shouldBe null
                inntektsmeldinger.first().getOrNull(InntektsmeldingEntitet.skjema) shouldBe skjema
                inntektsmeldinger.first().getOrNull(InntektsmeldingEntitet.dokument) shouldBe beriketDokument
            }

            val record = testRepo.hentRecordFraInntektsmelding(skjema.forespoerselId).shouldNotBeNull()

            record.getOrNull(InntektsmeldingEntitet.dokument) shouldBe beriketDokument

            inntektsmeldingRepo.hentNyesteBerikedeInnsendingId(skjema.forespoerselId) shouldBe innsendingId
        }

        test("skal lagre hvert innsendte skjema med ny innsendingId, men hente nyeste berikede inntektsmelding") {
            transaction {
                InntektsmeldingEntitet.selectAll().toList()
            }.shouldBeEmpty()

            val skjema = mockSkjemaInntektsmelding()

            val innsendingId1 = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema)
            val innsendingId2 = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema)
            inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema)

            innsendingId1 shouldNotBeEqual innsendingId2

            val beriketDokument = mockInntektsmeldingGammeltFormat().copy(tidspunkt = OffsetDateTime.now())

            inntektsmeldingRepo.oppdaterMedBeriketDokument(skjema.forespoerselId, innsendingId2, beriketDokument)
            inntektsmeldingRepo.hentNyesteBerikedeInnsendingId(skjema.forespoerselId) shouldBe innsendingId2

            inntektsmeldingRepo.oppdaterMedBeriketDokument(skjema.forespoerselId, innsendingId1, beriketDokument)
            inntektsmeldingRepo.hentNyesteBerikedeInnsendingId(skjema.forespoerselId) shouldBe innsendingId2
        }

        test("skal returnere im med gammelt inntekt-format ok") {
            transaction {
                InntektsmeldingEntitet.selectAll().toList()
            }.shouldBeEmpty()

            val skjema = mockSkjemaInntektsmelding()
            val inntektsmeldingGammeltFormat = mockInntektsmeldingGammeltFormat()

            val innsendingId = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema)

            inntektsmeldingRepo.oppdaterMedBeriketDokument(skjema.forespoerselId, innsendingId, inntektsmeldingGammeltFormat)

            transaction {
                InntektsmeldingEntitet
                    .selectAll()
                    .where {
                        (InntektsmeldingEntitet.forespoerselId eq skjema.forespoerselId.toString()) and
                            (InntektsmeldingEntitet.dokument eq inntektsmeldingGammeltFormat)
                    }.single()
            }
        }

        test("skal oppdatere journalpostId") {
            transaction {
                InntektsmeldingEntitet.selectAll().toList()
            }.shouldBeEmpty()

            val skjema = mockSkjemaInntektsmelding()
            val journalpost1 = randomDigitString(7)

            val innsendingId = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema)
            val beriketDokument = mockInntektsmeldingGammeltFormat().copy(tidspunkt = OffsetDateTime.now())
            inntektsmeldingRepo.oppdaterMedBeriketDokument(skjema.forespoerselId, innsendingId, beriketDokument)

            inntektsmeldingRepo.oppdaterJournalpostId(innsendingId, journalpost1)

            val record = testRepo.hentRecordFraInntektsmelding(skjema.forespoerselId)

            record.shouldNotBeNull()

            val journalPostId = record.getOrNull(InntektsmeldingEntitet.journalpostId)
            journalPostId.shouldNotBeNull()
        }

        test("skal oppdatere im med journalpostId") {
            val skjema = mockSkjemaInntektsmelding()
            val journalpostId = "jp-mollefonken-kjele"

            val innsendingId1 = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema)
            val innsendingId2 = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema)

            val beriketDokument = mockInntektsmeldingGammeltFormat().copy(tidspunkt = OffsetDateTime.now())
            inntektsmeldingRepo.oppdaterMedBeriketDokument(skjema.forespoerselId, innsendingId1, beriketDokument)
            inntektsmeldingRepo.oppdaterMedBeriketDokument(skjema.forespoerselId, innsendingId2, beriketDokument)

            // Skal kun oppdatere den andre av de to inntektsmeldingene
            inntektsmeldingRepo.oppdaterJournalpostId(innsendingId2, journalpostId)

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

        test("skal oppdatere inntektsmelding med journalpostId selv om den allerede har dette") {
            val skjema = mockSkjemaInntektsmelding()
            val gammelJournalpostId = "jp-traust-gevir"
            val nyJournalpostId = "jp-gallant-badehette"

            val innsendingId1 = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema)
            val innsendingId2 = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema)

            val beriketDokument = mockInntektsmeldingGammeltFormat().copy(tidspunkt = OffsetDateTime.now())
            inntektsmeldingRepo.oppdaterMedBeriketDokument(skjema.forespoerselId, innsendingId1, beriketDokument)
            inntektsmeldingRepo.oppdaterMedBeriketDokument(skjema.forespoerselId, innsendingId2, beriketDokument)

            inntektsmeldingRepo.oppdaterJournalpostId(innsendingId2, gammelJournalpostId)

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

            // Oppdater journalpostId
            inntektsmeldingRepo.oppdaterJournalpostId(innsendingId2, nyJournalpostId)

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
                resultsEtterNyJournalpostId[1][journalpostId] shouldBe nyJournalpostId
            }
        }

        test("skal _ikke_ oppdatere journalpostId for ekstern inntektsmelding") {
            val skjema = mockSkjemaInntektsmelding()
            val journalpostId = "jp-slem-fryser"

            val innsendingId1 = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema)

            val beriketDokument = mockInntektsmeldingGammeltFormat().copy(tidspunkt = OffsetDateTime.now())
            inntektsmeldingRepo.oppdaterMedBeriketDokument(skjema.forespoerselId, innsendingId1, beriketDokument)
            inntektsmeldingRepo.lagreEksternInntektsmelding(skjema.forespoerselId, mockEksternInntektsmelding())

            inntektsmeldingRepo.oppdaterJournalpostId(innsendingId1, journalpostId)

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
    })
