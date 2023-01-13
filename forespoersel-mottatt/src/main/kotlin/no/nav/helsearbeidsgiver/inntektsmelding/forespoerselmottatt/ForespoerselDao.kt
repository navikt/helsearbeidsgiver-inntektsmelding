package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt.db.RegisterVedtaksperiodeId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class ForespoerselDao(private val db: Database) {
    fun lagre(vedtaksperiodeId: UUID): UUID =
        transaction(db) {
            RegisterVedtaksperiodeId.run {
                insert {
                    it[this.vedtaksperiodeId] = vedtaksperiodeId
                } get forespoerselId
            }
        }
}
