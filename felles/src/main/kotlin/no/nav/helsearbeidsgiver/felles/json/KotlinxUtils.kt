package no.nav.helsearbeidsgiver.felles.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Person
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty

val personMapSerializer =
    MapSerializer(
        String.serializer(),
        Person.serializer()
    )

fun EventName.toJson(): JsonElement =
    toJson(EventName.serializer())

fun BehovType.toJson(): JsonElement =
    toJson(BehovType.serializer())

@JvmName("toJsonMapKeyStringValueString")
fun Map<String, String>.toJson(): JsonElement =
    toJson(
        MapSerializer(
            String.serializer(),
            String.serializer()
        )
    )

@JvmName("toJsonMapKeyKeyValueJsonElement")
fun Map<Key, JsonElement>.toJson(): JsonElement =
    toJson(
        MapSerializer(
            Key.serializer(),
            JsonElement.serializer()
        )
    )

fun JsonElement.toMap(): Map<Key, JsonElement> =
    fromJsonMapFiltered(Key.serializer())

fun <K : IKey, T : Any> K.lesOrNull(serializer: KSerializer<T>, melding: Map<K, JsonElement>): T? =
    melding[this]?.fromJson(serializer.nullable)

fun <K : IKey, T : Any> K.les(serializer: KSerializer<T>, melding: Map<K, JsonElement>): T =
    lesOrNull(serializer, melding)
        ?: throw IllegalArgumentException("Felt '$this' mangler i JSON-map.")

fun <K : IKey, T : Any> K.krev(krav: T, serializer: KSerializer<T>, melding: Map<K, JsonElement>): T =
    les(serializer, melding).also {
        if (it != krav) {
            throw IllegalArgumentException("Nøkkel '$this' har verdi '$it', som ikke matcher med påkrevd verdi '$krav'.")
        }
    }

fun Map<Key, JsonElement>.toPretty(): String =
    toJson().toPretty()
