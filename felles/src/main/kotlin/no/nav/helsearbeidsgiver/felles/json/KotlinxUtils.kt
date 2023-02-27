package no.nav.helsearbeidsgiver.felles.json

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer
import java.time.LocalDate
import java.util.UUID

fun <T : Any> T.toJson(serializer: KSerializer<T>): JsonElement =
    Json.encodeToJsonElement(serializer, this)

fun <T : Any> List<T>.toJson(elementSerializer: KSerializer<T>): JsonElement =
    toJson(
        ListSerializer(elementSerializer)
    )

fun String.toJson(): JsonElement =
    toJson(String.serializer())

fun LocalDate.toJson(): JsonElement =
    toJson(LocalDateSerializer)

fun UUID.toJson(): JsonElement =
    toJson(UuidSerializer)

fun Map<String, JsonElement>.toJson(): JsonElement =
    toJson(
        MapSerializer(
            String.serializer(),
            JsonElement.serializer()
        )
    )

inline fun <reified T : Any> JsonElement.fromJson(): T =
    Json.decodeFromJsonElement(this)

fun <T : Any> JsonElement.fromJson(ds: DeserializationStrategy<T>): T =
    Json.decodeFromJsonElement(ds, this)

fun String.parseJson(): JsonElement =
    Json.parseToJsonElement(this)

fun JsonNode.toJsonElement(): JsonElement =
    toString().parseJson()
