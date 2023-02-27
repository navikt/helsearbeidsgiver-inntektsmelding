package no.nav.helsearbeidsgiver.felles.json

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.time.LocalDate
import java.util.UUID

fun <T : Any> T.toJson(serializer: KSerializer<T>): JsonElement =
    Json.encodeToJsonElement(serializer, this)

fun String.toJson(): JsonElement =
    Json.encodeToJsonElement(this)

fun LocalDate.toJson(): JsonElement =
    toString().toJson()

fun UUID.toJson(): JsonElement =
    toString().toJson()

fun Enum<*>.toJson(): JsonElement =
    name.toJson()

fun Map<String, JsonElement>.toJson(): JsonElement =
    Json.encodeToJsonElement(this)

fun <T : Any> List<T>.toJson(elementToJson: (T) -> JsonElement): JsonElement =
    map { elementToJson(it) }.let(Json::encodeToJsonElement)

inline fun <reified T : Any> JsonElement.fromJson(): T =
    Json.decodeFromJsonElement(this)

fun <T : Any> JsonElement.fromJson(ds: DeserializationStrategy<T>): T =
    Json.decodeFromJsonElement(ds, this)

fun String.parseJson(): JsonElement =
    Json.parseToJsonElement(this)

fun JsonNode.toJsonElement(): JsonElement =
    toString().parseJson()
