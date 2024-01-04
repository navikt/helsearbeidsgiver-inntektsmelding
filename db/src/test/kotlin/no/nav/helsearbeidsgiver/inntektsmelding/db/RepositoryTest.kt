package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.ForespoerselEntitet
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.InntektsmeldingEntitet
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

class TestRepo(private val db: Database) {

    fun hentRecordFraInntektsmelding(forespørselId: String): ResultRow? {
        return transaction(db) {
            InntektsmeldingEntitet.run {
                select { (forespoerselId eq forespørselId) }
            }.firstOrNull()
        }
    }

    fun hentRecordFraForespoersel(forespørselId: String): ResultRow? {
        return transaction(db) {
            ForespoerselEntitet.run {
                select { (forespoerselId eq forespørselId) }
            }.firstOrNull()
        }
    }
}

class RepositoryTest : FunSpecWithDb(listOf(InntektsmeldingEntitet, ForespoerselEntitet), { db ->

    val foresporselRepo = ForespoerselRepository(db.db)
    val inntektsmeldingRepo = InntektsmeldingRepository(db.db)
    val testRepo = TestRepo(db.db)
    val ORGNR = "orgnr-456"

    test("skal lagre forespørsel") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val UUID = "abc-123"

        foresporselRepo.lagreForespoersel(UUID, ORGNR)

        shouldNotThrowAny {
            transaction {
                ForespoerselEntitet.select {
                    all(
                        ForespoerselEntitet.forespoerselId eq UUID,
                        ForespoerselEntitet.orgnr eq ORGNR
                    )
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

        val UUID = randomUuid()
        val DOK_1 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = OffsetDateTime.now())

        foresporselRepo.lagreForespoersel(UUID.toString(), ORGNR)
        inntektsmeldingRepo.lagreInntektsmelding(UUID.toString(), DOK_1)

        transaction {
            InntektsmeldingEntitet.select {
                all(
                    InntektsmeldingEntitet.forespoerselId eq UUID.toString(),
                    InntektsmeldingEntitet.dokument eq DOK_1
                )
            }.single()
        }
        // lagre varianter:
        inntektsmeldingRepo.lagreInntektsmelding(UUID.toString(), INNTEKTSMELDING_DOKUMENT_MED_TOM_FORESPURT_DATA)
        val im = inntektsmeldingRepo.hentNyeste(UUID)
        im shouldBe INNTEKTSMELDING_DOKUMENT_MED_TOM_FORESPURT_DATA

        inntektsmeldingRepo.lagreInntektsmelding(UUID.toString(), INNTEKTSMELDING_DOKUMENT_MED_FORESPURT_DATA)
        val im2 = inntektsmeldingRepo.hentNyeste(UUID)
        im2?.forespurtData shouldNotBe(null)
        im2?.forespurtData shouldBe INNTEKTSMELDING_DOKUMENT_MED_FORESPURT_DATA.forespurtData

        inntektsmeldingRepo.lagreInntektsmelding(UUID.toString(), INNTEKTSMELDING_DOKUMENT_MED_FORESPURT_DATA.copy(fullLønnIArbeidsgiverPerioden = null))
        val im3 = inntektsmeldingRepo.hentNyeste(UUID)
        im3?.fullLønnIArbeidsgiverPerioden shouldBe null
    }

    test("skal returnere im med gammelt inntekt-format ok") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()
        transaction {
            ForespoerselEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val UUID = "abc-1234"
        val DOK_1 = INNTEKTSMELDING_DOKUMENT_GAMMELT_INNTEKTFORMAT

        foresporselRepo.lagreForespoersel(UUID, ORGNR)
        inntektsmeldingRepo.lagreInntektsmelding(UUID, DOK_1)

        transaction {
            InntektsmeldingEntitet.select {
                all(
                    InntektsmeldingEntitet.forespoerselId eq UUID,
                    InntektsmeldingEntitet.dokument eq DOK_1
                )
            }.single()
        }
    }

    test("skal oppdatere journalpostId") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val UUID = randomUuid()
        val DOK_1 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = OffsetDateTime.now())
        val JOURNALPOST_1 = "jp-1"

        foresporselRepo.lagreForespoersel(UUID.toString(), ORGNR)
        inntektsmeldingRepo.lagreInntektsmelding(UUID.toString(), DOK_1)
        inntektsmeldingRepo.oppdaterJournalpostId(UUID, JOURNALPOST_1)
        val record = testRepo.hentRecordFraInntektsmelding(UUID.toString())
        record.shouldNotBeNull()
        val journalPostId = record.getOrNull(InntektsmeldingEntitet.journalpostId)
        journalPostId.shouldNotBeNull()
    }

    test("skal _ikke_ oppdatere journalpostId for ekstern inntektmelding") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val forespoerselId = UUID.randomUUID()
        val journalpostId = "jp-slem-fryser"

        foresporselRepo.lagreForespoersel(forespoerselId.toString(), ORGNR)
        inntektsmeldingRepo.lagreInntektsmelding(forespoerselId.toString(), INNTEKTSMELDING_DOKUMENT)
        inntektsmeldingRepo.lagreEksternInntektsmelding(forespoerselId.toString(), EKSTERN_INNTEKTSMELDING_DOKUMENT)

        inntektsmeldingRepo.oppdaterJournalpostId(forespoerselId, journalpostId)

        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }
            .forEach {
                if (it[InntektsmeldingEntitet.dokument] != null) {
                    it[InntektsmeldingEntitet.journalpostId] shouldBe journalpostId
                } else {
                    it[InntektsmeldingEntitet.eksternInntektsmelding].shouldNotBeNull()
                    it[InntektsmeldingEntitet.journalpostId].shouldBeNull()
                }
            }
    }

    test("skal oppdatere sakId") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val UUID = "abc-456"
        val SAK_ID_1 = "sak1-1"

        foresporselRepo.lagreForespoersel(UUID, ORGNR)
        foresporselRepo.oppdaterSakId(UUID, SAK_ID_1)
        val record = testRepo.hentRecordFraForespoersel(UUID)
        record.shouldNotBeNull()
        val sakId = record.getOrNull(ForespoerselEntitet.sakId)
        sakId.shouldNotBeNull()
        sakId.shouldBeEqualComparingTo(SAK_ID_1)
    }

    test("skal oppdatere oppgaveId") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val UUID = "abc-456"
        val OPPGAVE_ID_1 = "oppg-1"

        foresporselRepo.lagreForespoersel(UUID, ORGNR)
        foresporselRepo.oppdaterOppgaveId(UUID, OPPGAVE_ID_1)
        val rad = testRepo.hentRecordFraForespoersel(UUID)
        rad.shouldNotBeNull()
        val oppgaveId = rad.getOrNull(ForespoerselEntitet.oppgaveId)
        oppgaveId.shouldNotBeNull()
        oppgaveId.shouldBeEqualComparingTo(OPPGAVE_ID_1)
    }
})

private fun all(vararg conditions: Op<Boolean>): Op<Boolean> =
    conditions.reduce(Expression<Boolean>::and)
