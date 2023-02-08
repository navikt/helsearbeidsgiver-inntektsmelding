package no.nav.helsearbeidsgiver.inntektsmelding.db

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import no.nav.helsearbeidsgiver.felles.inntektsmelding.InntektsmeldingDokument
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object InntektsmeldingEntitet : Table("inntektsmelding") {
    val id = integer("id").autoIncrement(
        idSeqName = "inntektsmelding_id_seq"
    )
    val dokument = json<InntektsmeldingDokument>("dokument")
    val opprettet = datetime("opprettet")
    val uuid = text("uuid")
    override val primaryKey = PrimaryKey(id, name = "id")
}

inline fun <reified T : Any> Table.json(
    name: String,
    kSerializer: KSerializer<T> = serializer(),
    json: Json = Json { ignoreUnknownKeys = false }
): Column<T> =
    this.json(
        name = name,
        stringify = { json.encodeToString(kSerializer, it) },
        parse = { json.decodeFromString(kSerializer, it) }
    )

fun <T : Any> Table.json(name: String, stringify: (T) -> String, parse: (String) -> T): Column<T> =
    registerColumn(name, JsonColumnType(stringify, parse))

class JsonColumnType<T : Any>(private val stringify: (T) -> String, private val parse: (String) -> T) : ColumnType() {
    override fun sqlType(): String = "jsonb"
    override fun valueFromDB(value: Any) = parse(value as String)

    @Suppress("UNCHECKED_CAST")
    override fun notNullValueToDB(value: Any) = stringify(value as T)

    override fun valueToString(value: Any?): String = when (value) {
        is Iterable<*> -> notNullValueToDB(value)
        else -> super.valueToString(value)
    }
}
