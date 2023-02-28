package no.nav.helsearbeidsgiver.inntektsmelding.db

import kotlinx.serialization.KSerializer
import no.nav.helsearbeidsgiver.felles.inntektsmelding.db.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJsonStr
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object InntektsmeldingEntitet : Table("inntektsmelding") {
    val id = integer("id").autoIncrement(
        idSeqName = "inntektsmelding_id_seq"
    )
    val dokument = json("dokument", InntektsmeldingDokument.serializer())
    val opprettet = datetime("opprettet")
    val uuid = text("uuid")
    val journalpostId = varchar("journalpostid", 30).nullable()
    override val primaryKey = PrimaryKey(id, name = "id")
}

private fun <T : Any> Table.json(
    name: String,
    serializer: KSerializer<T>
): Column<T> =
    registerColumn(
        name = name,
        type = JsonColumnType(serializer)
    )

class JsonColumnType<T : Any>(private val serializer: KSerializer<T>) : ColumnType() {
    override fun sqlType(): String =
        "jsonb"

    override fun valueFromDB(value: Any): T =
        (value as String).fromJson(serializer)

    @Suppress("UNCHECKED_CAST")
    override fun notNullValueToDB(value: Any): String =
        (value as T).toJsonStr(serializer)

    override fun valueToString(value: Any?): String =
        when (value) {
            is Iterable<*> -> notNullValueToDB(value)
            else -> super.valueToString(value)
        }
}
