package no.nav.helsearbeidsgiver.felles.json

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.loeser.Løsning
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer
import java.time.LocalDate
import java.util.UUID

fun <T : Any> T.toJson(serializer: KSerializer<T>): JsonElement =
    Json.encodeToJsonElement(serializer, this)

fun <T : Any> List<T>.toJson(elementSerializer: KSerializer<T>): JsonElement =
    toJson(
        elementSerializer.list()
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

fun <T : Any> JsonElement.fromJson(serializer: KSerializer<T>): T =
    Json.decodeFromJsonElement(serializer, this)

fun String.parseJson(): JsonElement =
    Json.parseToJsonElement(this)

fun JsonNode.toJsonElement(): JsonElement =
    toString().parseJson()

fun <T : Any> KSerializer<T>.list(): KSerializer<List<T>> =
    ListSerializer(this)

fun <T : Any> KSerializer<T>.set(): KSerializer<Set<T>> =
    SetSerializer(this)

fun <T : Any> KSerializer<T>.løsning(): KSerializer<Løsning<T>> =
    Løsning.serializer(this)
