package no.nav.helsearbeidsgiver.felles.test.json

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.felles.json.toJson

fun JsonElement.fromJsonMapOnlyKeys(): Map<Key, JsonElement> =
    fromJsonMapFiltered(Key.serializer())

fun Map<Key, JsonElement>.toJson(): JsonElement =
    toJson(
        MapSerializer(
            Key.serializer(),
            JsonElement.serializer()
        )
    )
