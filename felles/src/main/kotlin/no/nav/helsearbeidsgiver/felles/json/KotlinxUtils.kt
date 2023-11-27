package no.nav.helsearbeidsgiver.felles.json

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson

fun JsonNode.toJsonElement(): JsonElement =
    toString().parseJson()

fun EventName.toJson(): JsonElement =
    toJson(EventName.serializer())

fun BehovType.toJson(): JsonElement =
    toJson(BehovType.serializer())

fun JsonElement.toMap(): Map<IKey, JsonElement> =
    listOf(
        Key.serializer(),
        Pri.Key.serializer()
    )
        .fold(emptyMap()) { jsonMap, keySerializer ->
            jsonMap + fromJsonMapFiltered(keySerializer)
        }

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
