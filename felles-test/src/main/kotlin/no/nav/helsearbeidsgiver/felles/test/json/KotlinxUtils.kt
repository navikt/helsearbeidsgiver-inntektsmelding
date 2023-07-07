package no.nav.helsearbeidsgiver.felles.test.json

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.toJson

fun JsonElement.fromJsonMapOnlyKeys(): Map<Key, JsonElement> =
    fromJsonMapFiltered(Key.serializer())

fun JsonElement.fromJsonMapOnlyDatafelter(): Map<DataFelt, JsonElement> =
    fromJsonMapFiltered(DataFelt.serializer())

fun JsonElement.fromJsonMapAllKeys(): Map<IKey, JsonElement> = fromJsonMapFiltered(Key.serializer()) + fromJsonMapFiltered(DataFelt.serializer())

fun Map<Key, JsonElement>.toJson(): JsonElement =
    toJson(
        MapSerializer(
            Key.serializer(),
            JsonElement.serializer()
        )
    )
