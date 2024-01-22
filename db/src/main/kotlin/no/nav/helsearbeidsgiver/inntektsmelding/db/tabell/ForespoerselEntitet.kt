package no.nav.helsearbeidsgiver.inntektsmelding.db.tabell

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object ForespoerselEntitet : Table("forespoersel") {
    val forespoerselId = varchar(name = "forespoersel_id", length = 40)
    val orgnr = text("orgnr")
    val sakId = varchar("sak_id", 36).nullable()
    val oppgaveId = varchar("oppgave_id", 36).nullable()
    val opprettet = datetime("opprettet")
}
