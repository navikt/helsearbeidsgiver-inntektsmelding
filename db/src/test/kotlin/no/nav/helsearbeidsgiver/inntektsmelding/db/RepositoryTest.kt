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

    test("skal persistere inntektsmelding") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val UUID = "abc-123"

        repository.lagre(UUID, INNTEKTSMELDING_DOKUMENT)

        shouldNotThrowAny {
            transaction {
                InntektsmeldingEntitet.select {
                    all(
                        InntektsmeldingEntitet.id eq 1,
                        InntektsmeldingEntitet.dokument eq INNTEKTSMELDING_DOKUMENT,
                        InntektsmeldingEntitet.uuid eq UUID
                    )
                }.single()
            }
        }
    }

    test("skal hente inntektsmeldinger nyeste først") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val UUID = "abc-456"
        val DOK_1 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = ZonedDateTime.now().toOffsetDateTime())
        val DOK_2 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = ZonedDateTime.now().toOffsetDateTime())
        val DOK_3 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = ZonedDateTime.now().toOffsetDateTime())

        repository.lagre(UUID, DOK_1)
        repository.lagre(UUID, DOK_2)
        repository.lagre(UUID, DOK_3)

        val dok = repository.hentNyeste(UUID)
        dok.shouldBe(DOK_3)
    }

    test("skal oppdatere journalpostId") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val UUID = "abc-456"
        val DOK_1 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = ZonedDateTime.now().toOffsetDateTime())
        val DOK_2 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = ZonedDateTime.now().toOffsetDateTime())
        val JOURNALPOST_1 = "jp-1"
        val JOURNALPOST_2 = "jp-2"

        repository.lagre(UUID, DOK_1)
        repository.oppdaterJournapostId(JOURNALPOST_1, UUID)
        val dok1 = repository.hentNyeste(UUID)
        dok1.shouldBe(DOK_1)

        repository.lagre(UUID, DOK_2)
        repository.oppdaterJournapostId(JOURNALPOST_2, UUID)
        val dok2 = repository.hentNyeste(UUID)
        dok2.shouldBe(DOK_2)
    }

    test("skal oppdatere sakId") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val UUID = "abc-456"
        val DOK_1 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = ZonedDateTime.now().toOffsetDateTime())
        val DOK_2 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = ZonedDateTime.now().toOffsetDateTime())
        val SAK_ID_1 = "sak1-1"
        val SAK_ID_2 = "sak1-2"

        repository.lagre(UUID, DOK_1)
        repository.oppdaterJournapostId(SAK_ID_1, UUID)
        val dok1 = repository.hentNyeste(UUID)
        dok1.shouldBe(DOK_1)

        repository.lagre(UUID, DOK_2)
        repository.oppdaterJournapostId(SAK_ID_2, UUID)
        val dok2 = repository.hentNyeste(UUID)
        dok2.shouldBe(DOK_2)
    }

    test("skal oppdatere oppgaveId") {
        transaction {
            InntektsmeldingEntitet.selectAll().toList()
        }.shouldBeEmpty()

        val UUID = "abc-456"
        val DOK_1 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = ZonedDateTime.now().toOffsetDateTime())
        val DOK_2 = INNTEKTSMELDING_DOKUMENT.copy(tidspunkt = ZonedDateTime.now().toOffsetDateTime())
        val OPPGAVE_ID_1 = "jp-1"
        val OPPGAVE_ID_2 = "jp-2"

        repository.lagre(UUID, DOK_1)
        repository.oppdaterJournapostId(OPPGAVE_ID_1, UUID)
        val dok1 = repository.hentNyeste(UUID)
        dok1.shouldBe(DOK_1)

        repository.lagre(UUID, DOK_2)
        repository.oppdaterJournapostId(OPPGAVE_ID_2, UUID)
        val dok2 = repository.hentNyeste(UUID)
        dok2.shouldBe(DOK_2)
    }
})

private fun all(vararg conditions: Op<Boolean>): Op<Boolean> =
    conditions.reduce(Expression<Boolean>::and)
