package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.inntektsmelding.db.tabell.ForespoerselEntitet
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class ForespoerselRepository(
    private val db: Database,
) {
    fun lagreForespoersel(
        forespoerselId: String,
        organisasjonsnummer: String,
    ) {
        transaction(db) {
            ForespoerselEntitet.insert {
                it[this.forespoerselId] = forespoerselId
                it[orgnr] = organisasjonsnummer
                it[opprettet] = LocalDateTime.now()
            }
        }
    }
}
