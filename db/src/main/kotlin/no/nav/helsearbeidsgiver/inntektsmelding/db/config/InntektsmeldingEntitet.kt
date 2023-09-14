package no.nav.helsearbeidsgiver.inntektsmelding.db.config

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.Jackson
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.jsonb

object InntektsmeldingEntitet : Table("inntektsmelding") {
    val id = integer("id").autoIncrement(
        idSeqName = "inntektsmelding_id_seq"
    )
    val forespoerselId = varchar(name = "forespoersel_id", length = 40) references ForespoerselEntitet.forespoerselId
    val dokument = jsonb<InntektsmeldingDokument>(
        name = "dokument",
        serialize = Jackson::toJson,
        deserialize = Jackson::fromJson
    ).nullable()
    val innsendt = datetime("innsendt")
    val journalpostId = varchar("journalpostid", 30).nullable()
    override val primaryKey = PrimaryKey(id, name = "id")
}
