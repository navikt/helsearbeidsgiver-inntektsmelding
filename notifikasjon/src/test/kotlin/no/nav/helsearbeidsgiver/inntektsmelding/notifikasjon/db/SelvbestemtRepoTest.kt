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

class SelvbestemtRepoTest : FunSpecWithDb(listOf(SelvbestemtSak), { db ->

    val selvbestemtRepo = SelvbestemtRepo(db.db)

    test("lagrer sak-ID") {
        val selvbestemtId = UUID.randomUUID()
        val sakId = "trallende-sarkofag"

        val antallLagret = selvbestemtRepo.lagreSakId(selvbestemtId, sakId)

        antallLagret shouldBeExactly 1

        val alleSaker = lesAlleSaker(db.db)

        alleSaker shouldHaveSize 1
        alleSaker.first().also { lagret ->
            lagret[SelvbestemtSak.selvbestemtId] shouldBe selvbestemtId
            lagret[SelvbestemtSak.sakId] shouldBe sakId
            lagret[SelvbestemtSak.slettes].toLocalDate() shouldBe LocalDate.now().plusDays(sakLevetid.inWholeDays)
        }
    }

    test("lagrer ikke sak-ID ved konflikt på selvbestemt-ID") {
        val selvbestemtId = UUID.randomUUID()
        val sakId1 = "sensitiv-xylofon"
        val sakId2 = "kampklar-banan"

        selvbestemtRepo.lagreSakId(selvbestemtId, sakId1)

        lesAlleSaker(db.db) shouldHaveSize 1

        shouldThrowExactly<ExposedSQLException> {
            selvbestemtRepo.lagreSakId(selvbestemtId, sakId2)
        }
    }

    test("lagrer ikke sak-ID ved konflikt på sak-ID") {
        val selvbestemtId1 = UUID.randomUUID()
        val selvbestemtId2 = UUID.randomUUID()
        val sakId = "brautende-flaske"

        selvbestemtRepo.lagreSakId(selvbestemtId1, sakId)

        lesAlleSaker(db.db) shouldHaveSize 1

        shouldThrowExactly<ExposedSQLException> {
            selvbestemtRepo.lagreSakId(selvbestemtId2, sakId)
        }
    }
})

private fun lesAlleSaker(db: Database): List<ResultRow> =
    transaction(db) {
        SelvbestemtSak.selectAll().toList()
    }
