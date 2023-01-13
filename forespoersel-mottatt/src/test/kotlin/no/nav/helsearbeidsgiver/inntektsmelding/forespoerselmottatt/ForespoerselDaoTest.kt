package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import no.nav.helsearbeidsgiver.felles.test.mock.MockUuid
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt.db.RegisterVedtaksperiodeId
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ForespoerselDaoTest : FunSpecWithDb(RegisterVedtaksperiodeId, { db ->

    val forespoerselDao = ForespoerselDao(db.db)

    test("lagrer vedtaksperiode-ID") {
        transaction {
            RegisterVedtaksperiodeId.selectAll()
                .toList()
        }
            .shouldBeEmpty()

        val forespoerselId = forespoerselDao.lagre(MockUuid.uuid)

        shouldNotThrowAny {
            transaction {
                RegisterVedtaksperiodeId.select {
                    all(
                        RegisterVedtaksperiodeId.id eq 1,
                        RegisterVedtaksperiodeId.forespoerselId eq forespoerselId,
                        RegisterVedtaksperiodeId.vedtaksperiodeId eq MockUuid.uuid
                    )
                }
                    .single()
            }
        }
    }
})

private fun all(vararg conditions: Op<Boolean>): Op<Boolean> =
    conditions.reduce(Expression<Boolean>::and)
