package no.nav.helsearbeidsgiver.felles.test.json

import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.model.Fail
import no.nav.helsearbeidsgiver.utils.json.fromJson

fun JsonElement.lesBehov(): BehovType? = Key.BEHOV.lesOrNull(BehovType.serializer(), this.toMap())

fun JsonElement.lesEventName(): EventName? = Key.EVENT_NAME.lesOrNull(EventName.serializer(), this.toMap())

fun JsonElement.lesData(): Map<Key, JsonElement> = this.toMap()[Key.DATA]?.toMap().orEmpty()

fun JsonElement.lesFail(): Fail = toMap()[Key.FAIL].shouldNotBeNull().fromJson(Fail.serializer())

fun Map<Key, JsonElement>.plusData(dataField: Pair<Key, JsonElement>): Map<Key, JsonElement> = plusData(mapOf(dataField))

fun Map<Key, JsonElement>.plusData(dataMap: Map<Key, JsonElement>): Map<Key, JsonElement> {
    val data = this[Key.DATA]?.toMap().orEmpty()
    return this.plus(
        Key.DATA to data.plus(dataMap).toJson(),
    )
}

fun Map<Key, JsonElement>.minusData(vararg dataKey: Key): Map<Key, JsonElement> {
    val data = this[Key.DATA]?.toMap().orEmpty()
    return this.plus(
        Key.DATA to data.minus(dataKey.toSet()).toJson(),
    )
}
