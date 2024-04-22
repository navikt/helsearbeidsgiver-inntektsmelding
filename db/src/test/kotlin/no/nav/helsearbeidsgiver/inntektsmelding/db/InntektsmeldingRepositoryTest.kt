package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.helsearbeidsgiver.felles.db.test.exposed.FunSpecWithDb
import no.nav.helsearbeidsgiver.inntektsmelding.db.tabell.InntektsmeldingEntitet
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

class InntektsmeldingRepositoryTest : FunSpecWithDb(listOf(InntektsmeldingEntitet), { db ->

    val inntektsmeldingRepo = InntektsmeldingRepository(db.db)
    val ORGNR = "orgnr-456"

    test("skal lagre inntektsmelding") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val forespoerselId = UUID.randomUUID()
        val DOK_1 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = OffsetDateTime.now())

        inntektsmeldingRepo.lagreInntektsmelding(forespoerselId.toString(), DOK_1)

        transaction {
            InntektsmeldingEntitet
                .selectAll()
                .where {
                    (InntektsmeldingEntitet.forespoerselId eq forespoerselId.toString()) and
                        (InntektsmeldingEntitet.dokument eq DOK_1)
                }
                .single()
        }
        // lagre varianter:
        inntektsmeldingRepo.lagreInntektsmelding(forespoerselId.toString(), INNTEKTSMELDING_DOKUMENT_MED_TOM_FORESPURT_DATA)
        val im = inntektsmeldingRepo.hentNyeste(forespoerselId)
        im shouldBe INNTEKTSMELDING_DOKUMENT_MED_TOM_FORESPURT_DATA

        inntektsmeldingRepo.lagreInntektsmelding(forespoerselId.toString(), INNTEKTSMELDING_DOKUMENT_MED_FORESPURT_DATA)
        val im2 = inntektsmeldingRepo.hentNyeste(forespoerselId)
        im2?.forespurtData shouldNotBe (null)
        im2?.forespurtData shouldBe INNTEKTSMELDING_DOKUMENT_MED_FORESPURT_DATA.forespurtData

        inntektsmeldingRepo.lagreInntektsmelding(
            forespoerselId.toString(),
            INNTEKTSMELDING_DOKUMENT_MED_FORESPURT_DATA.copy(fullLønnIArbeidsgiverPerioden = null)
        )
        val im3 = inntektsmeldingRepo.hentNyeste(forespoerselId)
        im3?.fullLønnIArbeidsgiverPerioden shouldBe null
    }

    test("skal returnere im med gammelt inntekt-format ok") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val forespoerselId = "abc-1234"
        val DOK_1 = INNTEKTSMELDING_DOKUMENT_GAMMELT_INNTEKTFORMAT

        inntektsmeldingRepo.lagreInntektsmelding(forespoerselId, DOK_1)

        transaction {
            InntektsmeldingEntitet
                .selectAll()
                .where {
                    (InntektsmeldingEntitet.forespoerselId eq forespoerselId) and
                        (InntektsmeldingEntitet.dokument eq DOK_1)
                }
                .single()
        }
    }

    test("skal oppdatere journalpostId") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val forespoerselId = UUID.randomUUID()
        val DOK_1 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = OffsetDateTime.now())
        val JOURNALPOST_1 = "jp-1"

        inntektsmeldingRepo.lagreInntektsmelding(forespoerselId.toString(), DOK_1)
        inntektsmeldingRepo.oppdaterJournalpostId(forespoerselId, JOURNALPOST_1)
        val record = db.db.lesInntektsmelding(forespoerselId)
        record.shouldNotBeNull()
        val journalPostId = record.getOrNull(InntektsmeldingEntitet.journalpostId)
        journalPostId.shouldNotBeNull()
    }

    test("skal kun oppdatere siste inntektsmelding med journalpostId") {
        val forespoerselId = UUID.randomUUID()
        val journalpostId = "jp-mollefonken-kjele"

        inntektsmeldingRepo.lagreInntektsmelding(forespoerselId.toString(), INNTEKTSMELDING_DOKUMENT)
        inntektsmeldingRepo.lagreInntektsmelding(forespoerselId.toString(), INNTEKTSMELDING_DOKUMENT)

        // Skal kun oppdatere siste
        inntektsmeldingRepo.oppdaterJournalpostId(forespoerselId, journalpostId)

        val resultat = transaction(db.db) {
            InntektsmeldingEntitet.selectAll()
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

        inntektsmeldingRepo.lagreInntektsmelding(forespoerselId.toString(), INNTEKTSMELDING_DOKUMENT)
        inntektsmeldingRepo.lagreInntektsmelding(forespoerselId.toString(), INNTEKTSMELDING_DOKUMENT)
        inntektsmeldingRepo.oppdaterJournalpostId(forespoerselId, gammelJournalpostId)

        val resultatFoerNyJournalpostId = transaction(db.db) {
            InntektsmeldingEntitet.selectAll()
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

        val resultsEtterNyJournalpostId = transaction(db.db) {
            InntektsmeldingEntitet.selectAll()
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

        inntektsmeldingRepo.lagreInntektsmelding(forespoerselId.toString(), INNTEKTSMELDING_DOKUMENT)
        inntektsmeldingRepo.lagreEksternInntektsmelding(forespoerselId.toString(), EKSTERN_INNTEKTSMELDING_DOKUMENT)

        inntektsmeldingRepo.oppdaterJournalpostId(forespoerselId, journalpostId)

        val resultat = transaction(db.db) {
            InntektsmeldingEntitet.selectAll()
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

private fun Database.lesInntektsmelding(forespoerselId: UUID): ResultRow? =
    transaction(this) {
        InntektsmeldingEntitet
            .selectAll()
            .where {
                InntektsmeldingEntitet.forespoerselId eq forespoerselId.toString()
            }
            .firstOrNull()
    }
