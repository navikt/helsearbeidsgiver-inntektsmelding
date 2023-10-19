package no.nav.helsearbeidsgiver.felles.test.json

import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.toJson

fun JsonElement.fromJsonMapOnlyKeys(): Map<Key, JsonElement> =
    fromJsonMapFiltered(Key.serializer())

fun JsonElement.fromJsonMapOnlyDatafelter(): Map<DataFelt, JsonElement> =
    fromJsonMapFiltered(DataFelt.serializer())

fun Map<Key, JsonElement>.toJson(): JsonElement =
    toJson(
        MapSerializer(
            Key.serializer(),
            JsonElement.serializer()
        )
    )

fun JsonElement.readFail(): Fail =
    toMap()[Key.FAIL].shouldNotBeNull().fromJson(Fail.serializer())
