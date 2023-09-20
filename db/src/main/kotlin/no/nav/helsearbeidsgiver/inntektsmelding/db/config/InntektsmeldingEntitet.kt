package no.nav.helsearbeidsgiver.inntektsmelding.db.config

import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.Jackson
import no.nav.helsearbeidsgiver.utils.json.jsonIgnoreUnknown
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.jsonb

object InntektsmeldingEntitet : Table("inntektsmelding") {
    val id = integer("id").autoIncrement(
        idSeqName = "inntektsmelding_id_seq"
    )
    val forespoerselId = varchar(name = "forespoersel_id", length = 40) references ForespoerselEntitet.forespoerselId
    val dokument = json("dokument", InntektsmeldingDokument::class.java).nullable()
    val eksternInntektsmelding = jsonb<EksternInntektsmelding>("ekstern_inntektsmelding", jsonIgnoreUnknown).nullable()
    val innsendt = datetime("innsendt")
    val journalpostId = varchar("journalpostid", 30).nullable()
    override val primaryKey = PrimaryKey(id, name = "id")
}

private fun <T : Any> Table.json(
    name: String,
    clazz: Class<T>
): Column<T> =
    registerColumn(
        name = name,
        type = JsonColumnType(clazz)
    )

class JsonColumnType<T : Any>(private val clazz: Class<T>) : ColumnType() {
    override fun sqlType(): String =
        "jsonb"

    override fun valueFromDB(value: Any): T = Jackson.objectMapper.readValue(value as String, clazz)

    override fun notNullValueToDB(value: Any): String =
        Jackson.toJson(value)

    override fun valueToString(value: Any?): String =
        when (value) {
            is Iterable<*> -> notNullValueToDB(value)
            else -> super.valueToString(value)
        }
}
