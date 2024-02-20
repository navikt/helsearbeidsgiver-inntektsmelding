package no.nav.helsearbeidsgiver.inntektsmelding.db.tabell

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.jsonb

object AapenInntektsmeldingEntitet : Table("aapen_inntektsmelding") {
    val id = integer("id").autoIncrement(
        idSeqName = "aapen_inntektsmelding_id_seq"
    )
    val aapenId = uuid("aapen_id")
    val inntektsmelding = jsonb<Inntektsmelding>(
        name = "inntektsmelding",
        jsonConfig = jsonConfig,
        kSerializer = Inntektsmelding.serializer()
    )
    val journalpostId = text("journalpost_id").nullable()
    val opprettet = datetime("opprettet")
}
