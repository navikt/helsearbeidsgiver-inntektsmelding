package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.ZonedDateTime

class RepositoryTest : FunSpecWithDb(InntektsmeldingEntitet, { db ->

    val repository = Repository(db.db)

    test("skal lagre forespørsel") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val UUID = "abc-123"

        repository.lagreForespørsel(UUID)

        shouldNotThrowAny {
            transaction {
                InntektsmeldingEntitet.select {
                    all(
                        InntektsmeldingEntitet.id eq 1,
                        InntektsmeldingEntitet.uuid eq UUID
                    )
                }.single()
            }
        }
    }

    test("skal oppdatere med dokument") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val UUID = "abc-123"
        val DOK_1 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = ZonedDateTime.now().toOffsetDateTime())

        repository.lagreForespørsel(UUID)
        repository.oppdaterDokument(UUID, DOK_1)

        transaction {
            InntektsmeldingEntitet.select {
                all(
                    InntektsmeldingEntitet.uuid eq UUID,
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

        repository.lagreForespørsel(UUID)
        repository.oppdaterDokument(UUID, DOK_1)
        repository.oppdaterJournapostId(JOURNALPOST_1, UUID)
        val dok1 = repository.hentNyeste(UUID)
        dok1.shouldBe(DOK_1)
    }

    test("skal oppdatere sakId") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val UUID = "abc-456"
        val DOK_1 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = ZonedDateTime.now().toOffsetDateTime())
        val SAK_ID_1 = "sak1-1"

        repository.lagreForespørsel(UUID)
        repository.oppdaterDokument(UUID, DOK_1)
        repository.oppdaterSakId(SAK_ID_1, UUID)
        val dok1 = repository.hentNyeste(UUID)
        dok1.shouldBe(DOK_1)
    }

    test("skal oppdatere oppgaveId") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val UUID = "abc-456"
        val DOK_1 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = ZonedDateTime.now().toOffsetDateTime())
        val OPPGAVE_ID_1 = "oppg-1"

        repository.lagreForespørsel(UUID)
        repository.oppdaterDokument(UUID, DOK_1)
        repository.oppdaterOppgaveId(OPPGAVE_ID_1, UUID)
        val dok1 = repository.hentNyeste(UUID)
        dok1.shouldBe(DOK_1)
    }
})

private fun all(vararg conditions: Op<Boolean>): Op<Boolean> =
    conditions.reduce(Expression<Boolean>::and)
