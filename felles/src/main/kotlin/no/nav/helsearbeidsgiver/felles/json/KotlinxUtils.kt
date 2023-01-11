package no.nav.helsearbeidsgiver.felles.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.util.UUID

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

fun JsonElement.toUuid(): UUID =
    jsonPrimitive
        .content
        .let(UUID::fromString)
