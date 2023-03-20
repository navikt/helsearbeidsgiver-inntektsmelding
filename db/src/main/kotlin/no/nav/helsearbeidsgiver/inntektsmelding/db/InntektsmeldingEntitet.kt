package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object InntektsmeldingEntitet : Table("inntektsmelding") {
    val id = integer("id").autoIncrement(
        idSeqName = "inntektsmelding_id_seq"
    )
    val dokument = json("dokument", InntektsmeldingDokument::class.java).nullable()
    val opprettet = datetime("opprettet")
    val uuid = text("uuid")
    val journalpostId = varchar("journalpostid", 30).nullable()
    val sakId = varchar("sakid", 36).nullable()
    val oppgaveId = varchar("oppgaveid", 36).nullable()
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

    override fun valueFromDB(value: Any): T = customObjectMapper().readValue(value as String, clazz)

    @Suppress("UNCHECKED_CAST")
    override fun notNullValueToDB(value: Any): String =
        customObjectMapper().writeValueAsString(value)

    override fun valueToString(value: Any?): String =
        when (value) {
            is Iterable<*> -> notNullValueToDB(value)
            else -> super.valueToString(value)
        }
}
