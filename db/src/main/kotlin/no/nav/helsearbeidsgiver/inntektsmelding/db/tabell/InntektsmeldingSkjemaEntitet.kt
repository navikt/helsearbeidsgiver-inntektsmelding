package no.nav.helsearbeidsgiver.inntektsmelding.db.tabell

import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.jsonb

object InntektsmeldingSkjemaEntitet : Table("inntektsmelding") {
    val id = integer("id").autoIncrement(
        idSeqName = "inntektsmelding_id_seq"
    )
    val forespoerselId = varchar(name = "forespoersel_id", length = 40) references ForespoerselEntitet.forespoerselId
    val dokument = jsonb<Innsending>(
        name = "dokument",
        jsonConfig = jsonConfig,
        kSerializer = Innsending.serializer()
    ).nullable()
    val innsendt = datetime("innsendt")
    val journalpostId = varchar("journalpostid", 30).nullable()
    override val primaryKey = PrimaryKey(id, name = "id")
}
