package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.felles.inntektsmelding.InntektsmeldingDokument
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class Repository(private val db: Database) {
    fun lagre(uuidLink: String, json: InntektsmeldingDokument): String =
        transaction(db) {
            InntektsmeldingEntitet.run {
                insert {
                    it[uuid] = uuidLink
                    it[dokument] = json
                    it[opprettet] = LocalDateTime.now()
                } get (uuid)
            }
        }

    fun hentNyeste(uuidLink: String): List<ResultRow> =
        transaction(db) {
            InntektsmeldingEntitet.run {
                select { (uuid eq uuidLink) }.orderBy(opprettet, SortOrder.DESC)
            }.take(100)
        }
}
