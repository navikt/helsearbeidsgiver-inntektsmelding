package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object ForespoerselSak : Table("forespoersel_sak") {
    val forespoerselId = uuid("forespoersel_id")
    val sakId = text("sak_id")
    val ferdigstilt = datetime("ferdigstilt").nullable()
    val slettes = datetime("slettes")
}

object ForespoerselOppgave : Table("forespoersel_oppgave") {
    val forespoerselId = uuid("forespoersel_id")
    val oppgaveId = text("oppgave_id")
    val ferdigstilt = datetime("ferdigstilt").nullable()
    val slettes = datetime("slettes")
}

object SelvbestemtSak : Table("selvbestemt_sak") {
    val selvbestemtId = uuid("selvbestemt_id")
    val sakId = text("sak_id")
    val slettes = datetime("slettes")
}
