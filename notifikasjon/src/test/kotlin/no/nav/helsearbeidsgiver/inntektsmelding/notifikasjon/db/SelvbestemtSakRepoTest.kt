package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.db.exposed.test.FunSpecWithDb
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.sakLevetid
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.UUID

class SelvbestemtSakRepoTest :
    FunSpecWithDb(listOf(SelvbestemtSak), { db ->

        val selvbestemtSakRepo = SelvbestemtSakRepo(db)

        test("lagrer sak-ID") {
            val selvbestemtId = UUID.randomUUID()
            val sakId = "trallende-sarkofag"

            selvbestemtSakRepo.lagreSakId(selvbestemtId, sakId)

            val alleSaker = db.lesAlleSaker()

            alleSaker shouldHaveSize 1
            alleSaker.first().also { lagret ->
                lagret[SelvbestemtSak.selvbestemtId] shouldBe selvbestemtId
                lagret[SelvbestemtSak.sakId] shouldBe sakId
                lagret[SelvbestemtSak.slettes].toLocalDate() shouldBe LocalDate.now().plusDays(sakLevetid.inWholeDays)
            }
        }

        test("lagrer ikke sak-ID ved konflikt på selvbestemt-ID, men kaster ikke feil") {
            val selvbestemtId = UUID.randomUUID()
            val sakId1 = "sensitiv-xylofon"
            val sakId2 = "kampklar-banan"

            selvbestemtSakRepo.lagreSakId(selvbestemtId, sakId1)

            db.lesAlleSaker() shouldHaveSize 1

            shouldNotThrowAny {
                selvbestemtSakRepo.lagreSakId(selvbestemtId, sakId2)
            }
        }

        test("lagrer ikke sak-ID ved konflikt på sak-ID, men kaster ikke feil") {
            val selvbestemtId1 = UUID.randomUUID()
            val selvbestemtId2 = UUID.randomUUID()
            val sakId = "brautende-flaske"

            selvbestemtSakRepo.lagreSakId(selvbestemtId1, sakId)

            db.lesAlleSaker() shouldHaveSize 1

            shouldNotThrowAny {
                selvbestemtSakRepo.lagreSakId(selvbestemtId2, sakId)
            }
        }
    })

private fun Database.lesAlleSaker(): List<ResultRow> =
    transaction(this) {
        SelvbestemtSak.selectAll().toList()
    }
