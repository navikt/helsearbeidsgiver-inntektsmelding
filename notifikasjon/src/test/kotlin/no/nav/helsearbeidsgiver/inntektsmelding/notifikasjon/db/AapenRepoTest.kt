package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.db.test.exposed.FunSpecWithDb
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.sakLevetid
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.UUID

class AapenRepoTest : FunSpecWithDb(listOf(AapenSak), { db ->

    val aapenRepo = AapenRepo(db.db)

    test("lagrer sak-ID") {
        val aapenId = UUID.randomUUID()
        val sakId = "trallende-sarkofag"

        val antallLagret = aapenRepo.lagreSakId(aapenId, sakId)

        antallLagret shouldBeExactly 1

        val alleSaker = lesAlleSaker(db.db)

        alleSaker shouldHaveSize 1
        alleSaker.first().also { lagret ->
            lagret[AapenSak.aapenId] shouldBe aapenId
            lagret[AapenSak.sakId] shouldBe sakId
            lagret[AapenSak.slettes].toLocalDate() shouldBe LocalDate.now().plusDays(sakLevetid.inWholeDays)
        }
    }

    test("lagrer ikke sak-ID ved konflikt på åpen-ID") {
        val aapenId = UUID.randomUUID()
        val sakId1 = "sensitiv-xylofon"
        val sakId2 = "kampklar-banan"

        aapenRepo.lagreSakId(aapenId, sakId1)

        lesAlleSaker(db.db) shouldHaveSize 1

        shouldThrowExactly<ExposedSQLException> {
            aapenRepo.lagreSakId(aapenId, sakId2)
        }
    }

    test("lagrer ikke sak-ID ved konflikt på sak-ID") {
        val aapenId1 = UUID.randomUUID()
        val aapenId2 = UUID.randomUUID()
        val sakId = "brautende-flaske"

        aapenRepo.lagreSakId(aapenId1, sakId)

        lesAlleSaker(db.db) shouldHaveSize 1

        shouldThrowExactly<ExposedSQLException> {
            aapenRepo.lagreSakId(aapenId2, sakId)
        }
    }
})

private fun lesAlleSaker(db: Database): List<ResultRow> =
    transaction(db) {
        AapenSak.selectAll().toList()
    }
