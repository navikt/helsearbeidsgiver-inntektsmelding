package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object AapenSak : Table("aapen_sak") {
    val aapenId = uuid("aapen_id")
    val sakId = text("sak_id")
    val slettes = datetime("slettes")
}
