package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.nulls.shouldNotBeNull
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.ZonedDateTime

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

        foresporselRepo.lagreForespørsel(UUID, ORGNR)

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

        val UUID = "abc-123"
        val DOK_1 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = ZonedDateTime.now().toOffsetDateTime())

        foresporselRepo.lagreForespørsel(UUID, ORGNR)
        inntektsmeldingRepo.lagreInntektsmeldng(UUID, DOK_1)

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

        val UUID = "abc-456"
        val DOK_1 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = ZonedDateTime.now().toOffsetDateTime())
        val JOURNALPOST_1 = "jp-1"

        foresporselRepo.lagreForespørsel(UUID, ORGNR)
        inntektsmeldingRepo.lagreInntektsmeldng(UUID, DOK_1)
        inntektsmeldingRepo.oppdaterJournapostId(JOURNALPOST_1, UUID)
        val record = testRepo.hentRecordFraInntektsmelding(UUID)
        record.shouldNotBeNull()
        val journalPostId = record.getOrNull(InntektsmeldingEntitet.journalpostId)
        journalPostId.shouldNotBeNull()
    }

    test("skal oppdatere sakId") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val UUID = "abc-456"
        val SAK_ID_1 = "sak1-1"

        foresporselRepo.lagreForespørsel(UUID, ORGNR)
        foresporselRepo.oppdaterSakId(SAK_ID_1, UUID)
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
        val DOK_1 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = ZonedDateTime.now().toOffsetDateTime())
        val OPPGAVE_ID_1 = "oppg-1"

        foresporselRepo.lagreForespørsel(UUID, ORGNR)
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
 /*




    test("skal oppdatere oppgaveId") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val UUID = "abc-456"
        val DOK_1 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = ZonedDateTime.now().toOffsetDateTime())
        val OPPGAVE_ID_1 = "oppg-1"

        repository.lagreForespørsel(UUID, ORGNR)
        repository.oppdaterDokument(UUID, DOK_1)
        repository.oppdaterOppgaveId(UUID, OPPGAVE_ID_1)
        val dok1 = repository.hentNyeste(UUID)
        dok1.shouldBe(DOK_1)
    }
})


*/
